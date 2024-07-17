---
# SPDX-FileCopyrightText: 2024 Jomco B.V.
# SPDX-FileCopyrightText: 2024 Topsector Logistiek
# SPDX-FileContributor: Joost Diepenmaat <joost@jomco.nl
# SPDX-FileContributor: Remco van 't Veer <remco@jomco.nl>
#
# SPDX-License-Identifier: AGPL-3.0-or-later

title: Bevindingen DIL VGU Demo Fase 2
date: 2024-07-17
author: 
  - Joost Diepenmaat <joost@jomco.nl>
  - Remco van 't Veer <remco@jomco.nl>
lang: nl
toc: false
---

Dit document beschrijft onze bevindingen bij de implementatie van de
DIL demo fase 2. We benoemen hier de pijnpunten, zodat deze als input
kunnen dienen voor verbeteringen aan architectuur, componenten en
documentatie.

# Focus fase 2: uitbesteden aan derde partij

In fase 1 blijft de oorspronkelijke vervoerder direct verantwoordelijk
voor het plannen van een chauffeur; als deze de rit wil uitbesteden
aan een externe partij, kan dat alleen door in het TMS van de
vervoerder de externe chauffeur en wagen in te plannen.

In fase 2 willen we demonstreren hoe een opdracht uitbesteed kan
worden aan een derde vervoerder die in het BDI netwerk is opgenomen,
en na uitbesteding zijn planning en bijbehorende autorisaties kan
beheren via het eigen TMS. We willen tegelijkertijd de afhankelijkheid
van online services zoveel mogelijk beperken.

Uitgangspunt voor de architectuur in fase 2 is dat we zoveel mogelijk
gebruik maken van bestaande iSHARE protocollen en componenten.

# Bevindingen

## Documentatie met betrekking tot Delegation Evidence chains

In iSHARE is het doorgeven van autorisaties ondersteund; autorisaties
worden weergegeven als *Delegation Evidence*, en onderdeel daarvan is
een `maxDelegationDepth`. Bij een keten van uitbestedingen zoals in
deze fase van de demo, machtigt iedere partij in de keten zijn
onderaannemer via een nieuwe *Delegation Evidence*.

De dienst *(Service Provider)* die de autorisatie van de uiteindelijk
uitvoerende partij *(Data Consumer)* moet controleren, heeft hiervoor
de *Delegation Evidence* van iedere partij in de keten nodig.

In de iSHARE / BDI documentatie wordt er weinig beschreven hoe de
*Service Provider* deze keten van autorisaties krijgt. In fase 2
hebben we verschillende keren een oplossingsrichting gekozen die bij
nader inzicht niet voldeed aan de specificaties en niet ondersteund
werdt door de gebruikte bestaande componenten.

De uiteindelijk gekozen oplossing houdt in dat bij de controle van
autorisaties, de *Data Consumer* een volledige lijst IDs van partijen
in de keten moet aanleveren, zodat de *Service Provider* bij al deze
partijen kan vragen of deze de opvolgende partij heeft gemachtigt. Dit
was relatief eenvoudig te implementeren, omdat er weinig technische
verschillen waren vergeleken met de Demo fase 1.

iSHARE RFC041 behandelt een deel van deze problemen.

## Vragen rond het bewaren en doorgeven Delegation Evidence

De gekozen oplossing zoals hierboven beschreven betekent dat een
controle van autorisaties alleen uitgevoerd kan worden als de
*Authorization Registries* van al deze partijen op dat moment online
zijn.

iSHARE biedt ook de mogelijkheid voor een *Data Consumer* om zelf
*Delegation Evidence* aan te leveren bij het aanspreken van een
*Service Provider*. Dit zou een mogelijkheid kunnen bieden om minder
afhankelijk te worden van online *Authoriation Registries*; als de
*Data Consumer* op een of andere manier de volledige keten van
*Delegation Evidence* bewaard tot het moment waar autorisatie nodig
is, en deze kan doorgeven aan de *Service Provider*.

Deze strategie is duidelijk beschreven in de iSHARE documentatie voor
de gevallen waar er één *Delegation Evidence* wordt opgeleverd, maar
er is geen beschrijving hoe dit zou moeten werken als er een keten van
*Delegation Evidence* nodig is voor volledige autorisatie.

Bijkomend probleem is dat *Delegation Evidence* geleverd wordt als
getekende JWT met een verval tijd (*exp* attribuut) van 30
seconden. In principe is de JWT na 30 seconden niet meer
bruikbaar. Het is niet duidelijk hoe hier mee om te gaan; ook omdat
het verlengen van vervaltijd het risico vergroot dat een autorisatie
sinds afgifte van de *Delegation Evidence* is ingetrokken, en hiervoor
geen mechanisme is beschreven in de iSHARE specificaties.

Er lijkt dan ook geen betrouwbare oplossing mogelijk binnen het
huidige iSHARE framework die niet afhankelijk is van meerdere online
services. De afhankelijkheid van online services is een probleem dat
groeit als het aantal benodigde services groeit (als de
uitbestedingsketen langer wordt).

## Alternatieve oplossingsrichtingen

De bovenstaande problemen zijn gerelateerd aan het *Delegation
Evidence* model zoals gebruikt in iSHARE. Binnen het project team
wordt ook gekeken naar alternatieven waarmee vooral de afhankelijkheid
van online services beperkt kan worden. De *Verifiable Credentials*
specificatie lijkt een goede kandidaat voor een altenatieve richting.

# Conclusie

Functioneel is fase 2 uitgevoerd zoals bedacht; de geplande use case
is volledig te demonstreren inclusief het beheer van autorisaties door
de juiste partijen.

Doordat de oplossingsrichting aangepast moest worden is de
afhankelijkheid van online service, en daarmee het risico op afbreuk
van de usecase bij netwerk problemen, niet verholpen. Dit vereist
aanpassingen aan de BDI/iSHARE specificaties, of een alternatieve
techniek, zoals Verifiable Credentials.

# Referenties

  - iSHARE RFC041: "Optimise delegation path discovery"
  [https://gitlab.com/ishare-foundation/cab/rfc/-/issues/4](https://gitlab.com/ishare-foundation/cab/rfc/-/issues/4)

  - W3C Verifiable Credentials Data Model v1.1
  [https://www.w3.org/TR/vc-data-model/](https://www.w3.org/TR/vc-data-model/)

  - iSHARE RFC040: "Verifiable Credentials Support"
  [https://gitlab.com/ishare-foundation/cab/rfc/-/issues/5](https://gitlab.com/ishare-foundation/cab/rfc/-/issues/5)
  

<!-- Local Variables: -->
<!-- ispell-local-dictionary: "dutch" -->
<!-- End: -->
