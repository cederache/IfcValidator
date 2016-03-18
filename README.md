IfcValidator
==========

Checking IFC models on the quality of the data.
Implemented parts of the Dutch "Rijksgebouwendienst BIM norm" as an example.

## Checks

A list of checks that have been identified by asking people from the building industry and reading Dutch "norm" documents that seem computer checkable.

| Check | Implemented | Part of |
| ------------- | ------------- | ----- | ------ | 
| Exactly 1 IfcProject | Yes | RVB_BIM_Norm |
| IfcProject has at least one representation where the TrueNorth attribute has been set | Yes | RVB_BIM_Norm |
| IfcProject has a length unit set | Yes | RVB_BIM_Norm |
| Length unit is either in Meters or Millimeters | Yes | RVB_BIM_Norm |
| IfcProject has an area unit | Yes | RVB_BIM_Norm |
| Area unit is in m2 | Yes | RVB_BIM_Norm |
| IfcProject has a volume unit | Yes | RVB_BIM_Norm |
| Volume unit is in m3 | Yes | RVB_BIM_Norm |
| Exactly 1 IfcSite | Yes | RVB_BIM_Norm |
| [Dutch]Kadastrale aanduidingen | Yes | RVB_BIM_Norm 1.1 2.2.7.2 |
| IfcSite has lattitude | Yes | RVB_BIM_Norm 1.1 |
| IfcSite has longitude | Yes | RVB_BIM_Norm 1.1 |
| IfcSite has elevation | Yes | RVB_BIM_Norm 1.1 |
| Has at least one IfcBuilding | Yes | RVB_BIM_Norm 1.1 |
| Has at least one IfcBuildingStorey | Yes | RVB_BIM_Norm 1.1 |
| Building storeys naming according to RVB_BIM_Norm | Yes | RVB_BIM_Norm |
| [link]Building storeys with increasing numbers have increased center | Yes | RVB_BIM_Norm 1.1 |


## Eample

Screenshot from a validationreport generated by this plugins, shown in BIMvie.ws

![alt text](https://github.com/opensourceBIM/IfcValidator/blob/master/docs/img/screenshot.png "screenshot")
