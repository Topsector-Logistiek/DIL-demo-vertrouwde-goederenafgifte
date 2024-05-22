<!--
SPDX-FileCopyrightText: 2024 Jomco B.V.
SPDX-FileCopyrightText: 2024 Topsector Logistiek
SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>

SPDX-License-Identifier: AGPL-3.0-or-later
-->

# DIL - Demo Vertrouwde Goederenafgifte

This code simulates the following environments:

- [erp](http://localhost:8080/erp/)
- [tms](http://localhost:8080/tms/)
- [wms](http://localhost:8080/wms/)

## Run

Use the following environment variables to configure this demo:

- `PORT`: the port number to listen for HTTP requests, defaults to `8080`
- `STORE_FILE`: the file to store state in, defaults to `/tmp/dil-demo.edn`

Authentication:

- `AUTH_USER_PREFIX`: prefix of user name, defaults to `demo`
- `AUTH_PASS_MULTI`: number to multiply user number with for password, defaults to `31415`
- `AUTH_MAX_ACCOUNTS`: maximum number of user accounts

Datespace:

- `DATASPACE_ID`: The dataspace identifier this demo runs in
- `SATELLITE_ID`: EORI of the iSHARE satellite
- `SATELLITE_ENDPOINT`: URL to the iSHARE satellite

ERP:

- `ERP_EORI`: EORI used by ERP
- `ERP_KEY_FILE`: the file to read the ERP private key from
- `ERP_CHAIN_FILE`: the file to read the ERP certificate chain from
- `ERP_AR_ID`: EORI of the ERP authorization register
- `ERP_AR_ENDPOINT`: URL to the ERP authorization register

TMS:

- `TMS_EORI`: EORI used by TMS
- `TMS_KEY_FILE`: the file to read the TMS private key from
- `TMS_CHAIN_FILE`: the file to read the TMS certificate chain from
- `TMS_AR_ID`: EORI of the TMS authorization register
- `TMS_AR_ENDPOINT`: URL to the TMS authorization register

WMS:

- `WMS_EORI`: EORI used by WMS
- `WMS_KEY_FILE`: the file to read the WMS private key from
- `WMS_CHAIN_FILE`: the file to read the WMS certificate chain from

Run the web server with the following:

```sh
clojure -M -m dil-demo.core
```

Point a web browser to [http://localhost:8080](http://localhost:8080) and login with user `demo1` with password `31415`.

## Deploy

The following creates an uber-jar containing all necessary dependencies to run the demo environments:

```sh
make
```

This jar-file is runnable with:

```sh
java -jar target/dil-demo.jar
```

## Copying

Copyright (C) 2024 Jomco B.V.
Copyright (C) 2024 Topsector Logistiek

[AGPL-3.0-or-later](LICENSES/AGPL-3.0-or-later.txt)
