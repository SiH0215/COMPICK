"""Deterministic PCPartPicker CSV/JSONL normalization; never writes to the database."""
import argparse,csv,hashlib,json,re
from collections import Counter
from decimal import Decimal,ROUND_HALF_UP
from pathlib import Path

CATEGORIES={'cpu':'CPU','cpu-cooler':'CPU_COOLER','cooler':'CPU_COOLER','motherboard':'MAINBOARD','mainboard':'MAINBOARD','memory':'RAM','ram':'RAM','video-card':'GPU','gpu':'GPU','internal-hard-drive':'STORAGE','ssd':'STORAGE','hdd':'STORAGE','storage':'STORAGE','power-supply':'POWER_SUPPLY','power_supply':'POWER_SUPPLY','cpu_cooler':'CPU_COOLER','case':'CASE'}
FIELDS=['category','brand','name','price','rating_count','specs','image_url']
def normalize(row,fx=None):
    category=CATEGORIES.get(str(row.get('category','')).strip().lower())
    if not category: raise ValueError('unknown_category')
    name=str(row.get('name','')).strip();brand=str(row.get('brand','')).strip()
    if not name or not brand: raise ValueError('missing_identity')
    specs=row.get('specs',{})
    if isinstance(specs,str):specs=json.loads(specs)
    if not isinstance(specs,dict) or not specs:raise ValueError('invalid_specs')
    specs={str(k).strip():str(v).strip() for k,v in specs.items() if v is not None and str(v).strip()}
    if category=='CASE':
        specs.pop('External Volume',None);specs.pop('Power Supply',None)
        if 'Type' in specs:specs.setdefault('Form Factor',specs.pop('Type'))
    if category=='CPU':
        for before,after in [('Performance Core Clock','Core Clock'),('Performance Core Boost Clock','Boost Clock')]:
            if before in specs:specs.setdefault(after,specs.pop(before))
    raw=row.get('price')
    if raw in (None,''):
        if fx is None:raise ValueError('eur_exchange_rate_required')
        price=Decimal(str(row['price_eur']))*Decimal(str(fx))
    else:price=Decimal(str(raw).replace(',',''))
    if not price.is_finite() or price<=0:raise ValueError('invalid_price')
    price=int((price/100).quantize(Decimal('1'),rounding=ROUND_HALF_UP)*100)
    if price<100:raise ValueError('invalid_price')
    rating=int(row.get('rating_count') or 0)
    if rating<0:raise ValueError('negative_rating')
    image=str(row.get('image_url') or '').strip()
    if image and not image.startswith(('https://','http://')):raise ValueError('invalid_image_url')
    return dict(category=category,brand=brand,name=name,price=price,rating_count=rating,specs=json.dumps(specs,ensure_ascii=False,sort_keys=True,separators=(',',':')),image_url=image)
def fingerprint(row):
    return hashlib.sha256(json.dumps([row[k] for k in ['category','brand','name','specs']],ensure_ascii=False).encode()).hexdigest()
def process(rows,fx=None,limit=None):
    valid=[];rejected=Counter()
    for row in rows:
        try:valid.append(normalize(row,fx))
        except (ValueError,KeyError,TypeError,ArithmeticError) as e:rejected[str(e) if str(e) in ['unknown_category','missing_identity','invalid_specs','eur_exchange_rate_required','invalid_price','negative_rating','invalid_image_url'] else 'invalid_value']+=1
    valid.sort(key=lambda p:(p['category'],-p['rating_count'],p['price'],fingerprint(p)))
    unique={};dupes=0
    for p in valid:
        key=fingerprint(p)
        if key in unique:dupes+=1
        else:unique[key]=p
    result=[];counts=Counter()
    for p in unique.values():
        if limit is None or counts[p['category']]<limit:result.append(p);counts[p['category']]+=1
    return result,dict(rejected=rejected,duplicates_removed=dupes,category_counts=counts)
def literal(s):
    s=str(s)
    chunks=[s[i:i+900].replace("'","''") for i in range(0,len(s),900)] or ['']
    return ' || '.join("TO_CLOB('"+c+"')" for c in chunks) if len(chunks)>1 else "'"+chunks[0]+"'"
def number(specs,key):
    m=re.search(r'\d+',specs.get(key,''));return int(m.group()) if m else None
def sql(row):
    specs=json.loads(row['specs']);model=row['name'][:125]+' ['+fingerprint(row)[:16]+']'
    values={'product_name':row['name'][:255],'brand':row['brand'][:100],'model_name':model,'price':row['price'],'rating_count':row['rating_count'],'stock_quantity':10,'sales_status':'ON_SALE','image_url':row['image_url'],'spec_json':row['specs'], 'socket_type':specs.get('Socket'),'memory_type':specs.get('Memory Type'),'form_factor':specs.get('Form Factor'),'power_consumption':number(specs,'TDP'),'recommended_power':number(specs,'Recommended Power'),'gpu_length_mm':number(specs,'Length'),'max_gpu_length_mm':number(specs,'Max GPU Length'),'power_capacity_watt':number(specs,'Wattage')}
    # Reimport changes catalog fields but does not reset live stock or sales status.
    value=lambda v:'NULL' if v is None else str(v) if isinstance(v,int) else literal(v)
    on=' AND '.join('target.'+k+'='+value(values[k]) for k in ['brand','model_name'])
    update=','.join('target.'+k+'='+value(v) for k,v in values.items() if k not in ['stock_quantity','sales_status','model_name','brand'])
    return f"MERGE INTO PRODUCT target USING (SELECT category_id FROM CATEGORY WHERE category_name={literal(row['category'])}) source ON (target.category_id=source.category_id AND {on}) WHEN MATCHED THEN UPDATE SET {update},target.updated_at=SYSTIMESTAMP WHEN NOT MATCHED THEN INSERT (category_id,{','.join(values)},created_at) VALUES (source.category_id,{','.join(value(v) for v in values.values())},SYSTIMESTAMP);\n"
def main():
    a=argparse.ArgumentParser();a.add_argument('--input',type=Path,required=True);a.add_argument('--output-dir',type=Path,required=True);a.add_argument('--eur-krw',type=Decimal);a.add_argument('--per-category',type=int);args=a.parse_args()
    if args.per_category is not None and args.per_category<1:a.error('--per-category must be positive')
    data=args.input.read_bytes();text=data.decode('utf-8-sig');rows=list(csv.DictReader(text.splitlines())) if args.input.suffix.lower()=='.csv' else [json.loads(l) for l in text.splitlines() if l.strip()]
    if any(r.get('price') in (None,'') and r.get('price_eur') not in (None,'') for r in rows) and args.eur_krw is None:a.error('price_eur input requires an explicit --eur-krw')
    if args.eur_krw is not None and (not args.eur_krw.is_finite() or args.eur_krw<=0):a.error('exchange rate must be positive and finite')
    result,report=process(rows,args.eur_krw,args.per_category);args.output_dir.mkdir(parents=True,exist_ok=True)
    with (args.output_dir/'compick-products.csv').open('w',encoding='utf-8',newline='') as f:w=csv.DictWriter(f,fieldnames=FIELDS,lineterminator='\n');w.writeheader();w.writerows(result)
    categories=sorted(set(p['category'] for p in result));statements=['-- Generated catalog upsert. Existing live stock is preserved.\n']
    for c in categories:statements.append(f"MERGE INTO CATEGORY target USING (SELECT {literal(c)} category_name FROM DUAL) source ON (target.category_name=source.category_name) WHEN NOT MATCHED THEN INSERT(category_name) VALUES(source.category_name);\n")
    statements.extend(map(sql,result));(args.output_dir/'products.sql').write_text(''.join(statements),encoding='utf-8')
    report.update(source_sha256=hashlib.sha256(data).hexdigest(),input_rows=len(rows),output_rows=len(result),eur_krw=str(args.eur_krw),per_category=args.per_category,policy='same category/brand/name/specs deduplicated; highest rating then lowest price retained; 100 KRW rounding')
    (args.output_dir/'manifest.json').write_text(json.dumps(report,ensure_ascii=False,sort_keys=True,indent=2),encoding='utf-8');print(json.dumps(report,ensure_ascii=False))
if __name__=='__main__':main()
