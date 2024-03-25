# DIL - Demo Vertrouwde Goederenafgifte

This code simulates the following environments:

- [erp](http://localhost:8080/erp/)
- [tms](http://localhost:8080/tms/)
- [wms](http://localhost:8080/wms/)

## Run

Use the following environment variables to configure this demo:

- `PORT`: the port number to listen for HTTP requests, defaults to `8080`
- `STORE_FILE`: the file to store state in, defaults to `/tmp/dil-demo.edn`
- `ERP_EORI`: EORI used by ERP
- `ERP_KEY_FILE`: the file to read the ERP private key from
- `ERP_CHAIN_FILE`: the file to read the ERP certificate chain from
- `TMS_EORI`: EORI used by TMS
- `TMS_KEY_FILE`: the file to read the TMS private key from
- `TMS_CHAIN_FILE`: the file to read the TMS certificate chain from
- `WMS_EORI`: EORI used by WMS
- `WMS_KEY_FILE`: the file to read the WMS private key from
- `WMS_CHAIN_FILE`: the file to read the WMS certificate chain from

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
