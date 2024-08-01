# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl>
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
# SPDX-License-Identifier: AGPL-3.0-or-later

FROM clojure:temurin-21-tools-deps-1.11.1.1435 as builder

RUN mkdir /app
WORKDIR /app
COPY . /app/
RUN make target/dil-demo.jar

FROM gcr.io/distroless/java17-debian12
COPY --from=builder /app/target/dil-demo.jar /dil-demo.jar

WORKDIR /
CMD ["/dil-demo.jar"]

