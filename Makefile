default: target/dil-demo.jar

%.crt: %.p12
	openssl pkcs12 -in $< -chain -nokeys -legacy -out $@

%.pem: %.p12
	openssl pkcs12 -in $< -nocerts -nodes -legacy -out $@

pems: credentials/EU.EORI.NLPRECIOUSG.pem credentials/EU.EORI.NLSECURESTO.pem credentials/EU.EORI.NLSMARTPHON.pem

certs: credentials/EU.EORI.NLPRECIOUSG.crt credentials/EU.EORI.NLSECURESTO.crt credentials/EU.EORI.NLSMARTPHON.crt

classes/dil_demo/core.class: src/dil_demo/core.clj
	mkdir -p classes
	clj -M -e "(compile 'dil-demo.core)"

target/dil-demo.jar: classes/dil_demo/core.class
	clojure -M:uberjar --main-class dil-demo.core

clean:
	rm -rf classes target
