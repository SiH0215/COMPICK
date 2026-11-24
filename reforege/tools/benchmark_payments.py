"""Compare service latency with isolated H2 databases and a 500ms fake gateway.

No live payment, SMTP or Gemini calls are made. Requires Java 21 and Maven.
Example: python tools/benchmark_payments.py --before ../ --after .
"""
import argparse,json,os,shutil,subprocess,tempfile
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before',type=Path,required=True)
    parser.add_argument('--after',type=Path,default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output-dir',type=Path,default=Path('target/payment-comparison'))
    parser.add_argument('--java-home',type=Path)
    parser.add_argument('--maven',help='Maven executable; defaults to each project wrapper')
    parser.add_argument('--offline',action='store_true')
    args=parser.parse_args();output=args.output_dir.resolve();output.mkdir(parents=True,exist_ok=True)
    env=os.environ.copy()
    if args.java_home:env['JAVA_HOME']=str(args.java_home);env['PATH']=str(args.java_home/'bin')+os.pathsep+env.get('PATH','')
    java=str(args.java_home/'bin/java') if args.java_home else shutil.which('java')
    javac=str(args.java_home/'bin/javac') if args.java_home else shutil.which('javac')
    if not java or not javac:parser.error('Java 21 JDK is required')
    for key in ['GEMINI_API_KEY','TOSS_CLIENT_KEY','TOSS_SECRET_KEY','GOOGLE_CLIENT_ID','GOOGLE_CLIENT_SECRET']:env[key]=''
    properties="""spring.datasource.url=jdbc:h2:mem:payment_comparison;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.hikari.maximum-pool-size=2
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.open-in-view=false
spring.jpa.show-sql=false
spring.mail.host=localhost
gemini.api-key=
toss.payments.client-key=
toss.payments.secret-key=
payments.recovery-enabled=false
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
server.address=127.0.0.1
server.port=0
"""
    for label,root in [('before',args.before.resolve()),('after',args.after.resolve())]:
        if not (root/'pom.xml').is_file():parser.error('Missing pom.xml: '+str(root))
        wrapper=root/('mvnw.cmd' if os.name=='nt' else 'mvnw')
        maven=args.maven or str(wrapper)
        cpfile=root/'target/payment-comparison-classpath.txt'
        command=[maven,'-q',*(['-o'] if args.offline else []),'-DskipTests','test-compile','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-Dmdep.outputFile='+str(cpfile)]
        with (output/(label+'-build.log')).open('w',encoding='utf-8') as log:
            subprocess.run(command,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
        with tempfile.TemporaryDirectory(prefix='compick-benchmark-') as temporary:
            work=Path(temporary);config=work/'benchmark.properties';config.write_text(properties,encoding='utf-8')
            classes=work/'classes';classes.mkdir()
            cp=str(root/'target/classes')+os.pathsep+cpfile.read_text(encoding='utf-8').strip()
            subprocess.run([javac,'-encoding','UTF-8','-cp',cp,'-d',str(classes),str(Path(__file__).with_name('ComparisonLauncher.java'))],env=env,check=True)
            report=output/('payment-'+label+'.json')
            command=[java,'-cp',str(classes)+os.pathsep+cp,'-Dcompare.benchmark=true','-Dcompare.label='+label,'-Dcompare.output='+str(report),'ComparisonLauncher','--spring.config.location='+config.as_uri()]
            with (output/(label+'-runtime.log')).open('w',encoding='utf-8') as log:
                subprocess.run(command,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
            data=json.loads(report.read_text(encoding='utf-8'))
            if data['successful_orders']!=40 or data['verified_stock_rows']!=40:raise RuntimeError('Incomplete measurement: '+label)
            print(label+': 40 completed orders; output='+str(report))
if __name__=='__main__':main()
