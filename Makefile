default: target/dil-demo.jar

classes/dil_demo/core.class: src/dil_demo/core.clj
	mkdir -p classes
	clj -M -e "(compile 'dil-demo.core)"

target/dil-demo.jar: classes/dil_demo/core.class
	clojure -M:uberjar --main-class dil-demo.core

clean:
	rm -rf classes target
