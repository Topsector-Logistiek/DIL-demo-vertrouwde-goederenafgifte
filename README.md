# DIL demo

```sh
clojure -M -m dil-demo.core
```

- [erp](http://localhost:3000/erp/)
- [tms](http://localhost:3000/tms/)
- [wms](http://localhost:3000/wms/)

## Model

- ERP : OTM Consignment (sends Transport orders to TMS and WMS)
- TMS: OTM Trip (Transport order wrapped up with chauffeur and vehicle)
- WMS: OTM TransportOrder

Bases on OTM:

```clojure
(def consignment
  {:external-attributes {:ref "202401011200"}
   :status              "draft"

   :goods
   [{:association-type "inline"
     :entity           {:description "Box of bananas"}}]

   :actions
   [{:association-type "inline"
     :entity
     {:action-type "load"
      :start-time  "2024-01-01 12:00"
      :location    {:association-type "inline"
                    :entity
                    {:name          "Jumbo, Tilburg"
                     :type          "warehouse"
                     :geo-reference {}}}
      :remarks     "Blah blah"}}
    {:association-type "inline"
     :entity
     {:action-type "unload"
      :start-time  "2024-01-01 12:00"
      :location    {:association-type "inline"
                    :entity
                    {:name          "Intel, Schiphol"
                     :type          "warehouse"
                     :geo-reference {}}}
      :remarks     "Blah blah"}}]
                                       
   :actors
   [{:association-type "inline"
     :entity
     {:name  "de Jong Transport B.V."
      :roles #{"carrier"}}}]})

(def transport-order
  {:consignments [{:association-type "inline"
                   :entity           consignment}]})

(def trip
  {:status "draft"

   :vehicle
   [{:association-type "inline"
     :entity           {:license-plate "ABC123EF"}}]

   :actors
   [{:association-type "inline"
     :entity           {:name                "Fred"
                        :external-attributes {:id "NL1234567890"}}
     :roles            ["driver"]}]})
```
