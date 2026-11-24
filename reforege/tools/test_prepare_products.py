import unittest,importlib.util,tempfile,json,subprocess,sys
from pathlib import Path
spec=importlib.util.spec_from_file_location('prepare_products',Path(__file__).parent/'prepare_products.py');p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
class ProductPreparationTests(unittest.TestCase):
 def row(self,**kw):
  r=dict(category='cpu',brand='AMD',name='CPU',price='150000',rating_count='2',specs='{"TDP":"65 W"}',image_url='https://example.com/image.jpg');r.update(kw);return r
 def test_category_aliases_and_json_normalization(self):
  r=p.normalize(self.row(category='CASE',specs='{"Type":"ATX","External Volume":"1","Power Supply":"None"}'))
  self.assertEqual(json.loads(r['specs']),{'Form Factor':'ATX'})
 def test_duplicate_identity_includes_specs(self):
  rows,_=p.process([self.row(),self.row(price=140000),self.row(specs='{"TDP":"95 W"}')]);self.assertEqual(len(rows),2);self.assertEqual(min(x['price'] for x in rows),140000)
 def test_explicit_exchange_rate(self):
  with self.assertRaises(ValueError):p.normalize(self.row(price=None,price_eur='100'))
  self.assertEqual(p.normalize(self.row(price=None,price_eur='100'),1500)['price'],150000)
 def test_invalid_records_and_negative_ratings(self):
  rows,report=p.process([self.row(price=-1),self.row(specs='{'),self.row(rating_count=-1),self.row(category='unknown')]);self.assertEqual(rows,[]);self.assertEqual(sum(report['rejected'].values()),4)
 def test_deterministic_selection(self):
  source=[self.row(name=str(i),rating_count=i) for i in range(10)];a,_=p.process(source,limit=3);b,_=p.process(list(reversed(source)),limit=3);self.assertEqual(a,b);self.assertEqual([x['name'] for x in a],['9','8','7'])
 def test_sql_upsert_does_not_reset_live_stock(self):
  s=p.sql(p.normalize(self.row(name="CPU'quoted")));update=s.split('UPDATE SET')[1].split('WHEN NOT MATCHED')[0];self.assertNotIn('stock_quantity',update);self.assertIn("CPU''quoted",s)
 def test_repeat_run_identical_outputs(self):
  with tempfile.TemporaryDirectory() as d:
   root=Path(d);source=root/'input.jsonl';source.write_text(json.dumps(self.row()),encoding='utf-8')
   for directory in ['a','b']:subprocess.run([sys.executable,str(Path(p.__file__)),'--input',str(source),'--output-dir',str(root/directory)],check=True,capture_output=True)
   for name in ['compick-products.csv','products.sql','manifest.json']:self.assertEqual((root/'a'/name).read_bytes(),(root/'b'/name).read_bytes())
if __name__=='__main__':unittest.main()
