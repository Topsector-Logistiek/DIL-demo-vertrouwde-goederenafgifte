# DIL - Demo Vertrouwde Goederenafgifte

This code simulates the following environments:

- [erp](http://localhost:8080/erp/)
- [tms](http://localhost:8080/tms/)
- [wms](http://localhost:8080/wms/)

## Run

Use the following environment variables to configure this demo:

- `PORT`: the port number to listen for HTTP requests, defaults to `8080`
- `STORE_FILE`: the file to store state in, defaults to `/tmp/dil-demo.edn`

Run the web server with the following:

```sh
clojure -M -m dil-demo.core
```

## Deploy

The following creates an uber-jar containing all necessary dependencies to run the demo environments:

```sh
make
```

This jar-file is runnable with:

```sh
java -jar target/dil-demo.jar
```
