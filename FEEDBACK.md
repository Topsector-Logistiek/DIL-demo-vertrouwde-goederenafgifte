# iSHARE

Het is onduidelijk wat de leidende documentatie is van iSHARE.  We maken nu gebruik van 3 bronnen die enkele af en toe tegenspreken:

- https://dev.ishareworks.org/
- https://ishare-3.gitbook.io/ishare-trust-framework-collection
- https://app.swaggerhub.com/apis/iSHARE/i-share_satellite/1.0

## Satellite

- `/connect/token` geeft onverwachte status "202 Accepted" en inhoud "invalid client id" als "client_id" geen EORI is.  Dit zou waarschijnlijk een "400 Bad Request" of "422 Unprocessable Content" status moeten zijn.  Echter, volgens de documentation zou dit een "OpenID Connect 1.0 client ID" moeten toestaan (bron: [Access Token parameters](https://dev.ishareworks.org/common/token.html#parameters))welke ook andere waarden dan een EORI toestaat.

- `/parties/{party_id}` met een niet bestaat ID geeft een "200 OK" met een `party_info` dat leeg is.  Volgens de documentatie is dat niet valide (bron: [Swagger definitie /parties/{party_id}](https://app.swaggerhub.com/apis/iSHARE/i-share_satellite/1.0#/iSHARE%20Satellite/%2Fparties%2F%7Bparty_id%7D).  Gezien het ID op het pad staat, ligt het voor de hand dat hier een "404 Not Found" response terug wordt gegeven.  In de [dev.ishare](https://dev.ishareworks.org/satellite/parties.html#request) documentatie wordt deze variant van het parties endpoint niet beschreven en het is daarmee onduidelijk welke documentatie leidend is.

## Authorization Register (iSHARE)

- `/delegation` geeft onverwacht status "404 Not Found" bij het insturen van een delegation mask met een onbekend "target.accessSubject".  Dit is niet conform de documentatie (bron: [Delegation Endpoint HTTP status codes](https://dev.ishareworks.org/delegation/endpoint.html#http-status-codes)).

- `/policy` geeft status "200 OK" bij het succesvol opvoeren van een policy.  Het ligt meer voor de hand dat hier een "201 Created" status teruggegeven wordt.

Het lijkt onmogelijk om een policy in te trekken.

De policy zijn maar korte tijd beschikbaar in het AR, ongeacht de waarden van `notBefore` en `notOnOrAfter`.  Dit heeft waarschijnlijk te maken met het JWT token waarin dit policy gecodeerd staat en is voor M2M use cases niet altijd een probleem.  Voor deze use case echter wel en daarom is de huidige implementatie van het iSHARE AR niet geschikt.

## Authorization Register (Poort8)

- Geeft bij verkeerd gebruik "500 Internal Server Error" terug ipv meer informatieve "400 Bad Request" met uitleg.

- (was) Niet mogelijk om notBefore en notOnOrAfter door te gegeven in nieuwe policy.

- `/delegation` geeft bij afwijzing `notBefore` en `notOnOrAfter` as `-1` terug.  Dat is conform JSON schema (bron: [iSHARE Scheme Specification](https://app.swaggerhub.com/apis/iSHARE/iSHARE_Scheme_Specification/2.0#/jwt_payload_delegation_evidence_token)) maar niet heel nuttig.

# Delegation Evidence

Het antwoord bij het opvragen van DE is verwarrend vooral in het geval van een afwijzing.  Bij een afwijzing wordt de DE mask verpakt als DE terug geleverd met daarin de rules aangepast.  Dit heeft als probleem dat ingrediënten die missen in de mask erbij "verzonnen" moeten worden (bijvoorbeeld `notBefore`).  Het is ook niet duidelijk of er meerdere policies op een vraag terug kunnen komen en zoja welke conclusies daaruit genomen mogen worden.

Omdat het opvoeren van een DE niet gestandaardiseerd is, is het ook niet duidelijk hoe een policy weer ingetrokken moet worden.  Zo lijkt het AR van iSHARE geen mogelijkheid te hebben om een policy in te trekken maar is het theoretisch mogelijk een nieuw DE op te vroegen waar van de rules niet "Permit" maar "Deny" bevatten.  Wat `/delegation` dan teruggeeft is onduidelijk en slaat weer terug op het voorgaande punt over wat er gebeurd als er meerder policies voor een resource target bestaan.

## XACML?

FEEDACK: in hoeverre mogen we de XACML spec als betrouwbare
documentatie gebruiken? Het lijkt dat het antwoord "helemaal niet" is.

https://ishare-3.gitbook.io/ishare-trust-framework-collection/readme/detailed-descriptions/technical/structure-of-delegation-evidence

## Resource attributes

Deze zijn niet toepasbaar voor deze use cases.  Het is hier een "actie" op een resource waar het om gaat.

## JWT geldigheid tov notBefore en notOnOrAfter

Waarom is JWT geldigheid niet gelijk getrokken met notBefore en notOnOrAfter?

## target.environment

Het verschil tussen target.environment in de policysets en policies is onduidelijk en de inhoud ervan verhoud zich slecht tot deze use cases.

## policysets / target.environment.licenses

- Policy sets documentatie verwijst niet naar lijst met Licenses (bron: [Policy Sets](https://dev.ishareworks.org/delegation/policy-sets.html#refpolicysets)

- geen van de licenses is een goede match voor deze use case (bron: [Licenses in iShare Wiki](https://ishareworks.atlassian.net/wiki/spaces/IS/pages/70221903/Licenses)

- bovenstaande lijst is niet met "ISHARE." geprefixed maar in het voorbeeld op dev site wel (bron: [Decoded JWT Payload](https://dev.ishareworks.org/delegation/endpoint.html#decoded-jwt-payload)

## policies / target.environment.serviceProviders

- Volgens de documentatie is target.environment in deze context optioneel (bron: [Policies](https://dev.ishareworks.org/delegation/policy-sets.html#policies)) echter zowel de iSHARE als de Poort8 implementatie functioneren niet als deze niet gevuld is.  In de iSHARE implementatie wordt een lege lijst toegestaan (`[]`) en bij Poort8 komen we weg met `["Dummy"]`.

- Onduidelijk wat het doel hiervan is.  Moeten systemen zelf checken of ze op de lijst staan?

## resource beschrijving

Resource id is nu een "plat" opdrachtnummer, maar dit zou
een URN moeten zijn.

https://ishare-3.gitbook.io/ishare-trust-framework-collection/readme/detailed-descriptions/technical/structure-of-delegation-evidence

# iSHARE protocol algemeen

We gebruiken nu voor access subject voor chauffeur ID de
laatste cijfers van ID + kenteken van trekker.  dit is niet
compliant met iSHARE spec -- daar zou het altijd een iSHARE
identifier moeten zijn maar

 1. ishare identificeert alleen rechtspersonen met een
    EORI (bedrijven en instellingen)

 2. ishare deelnemers zijn ook alleen rechtspersonen en iSHARE heeft
   eigenlijk geen concept van personen die individueel authorisaties
   krijgen.

# OpenTripModel

We gebruiken nu de "owner" actor om het EORI van de verlader op te slaan.  Dit is waarschijnlijk niet de juiste manier omdat de verlader niet meer dan de opdrachtgever van een transport opdracht is en niet de eigenaar van de goederen hoeft te zijn.  Er is geen beter actor type voorhanden.
