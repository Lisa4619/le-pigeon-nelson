<?php



$radiusPlayable = 150; /* meters */


include 'pigeon-nelson.php';




function addParc($row, $server) {
    global $radiusPlayable;

    $name = $row["tags"]["name"];
    $coordinate = PNUtil::osm2geokit($row);
    
 
    $name = "Vous êtes proche du parc du creux de l'enfer" . $name;
    

    $message = PigeonNelsonMessage::makeTxtMessage($name, "fr");
    $message->addRequiredCondition(PigeonNelsonCondition::ConditionDistanceTo($coordinate, Comparison::lessThan, $radiusPlayable));
    
    $server->addMessage($message);
    
    return $coordinate;
}




$server = new PigeonNelsonServer($_GET);

$server->setName("Parc du creux de l'enfer");
$server->setDescription("Savoir si vous êtes proches du parc du creux de l'enfer");
$server->setEncoding("UTF-8");
$server->setDefaultPeriodBetweenUpdates(0);


if ($server->isRequestedSelfDescription()) {
    print $server->getSelfDescription();
    return;
}


// coordinates is required
if (!$server->hasCoordinatesRequest()) {
    echo "[]";
    return;
}


$server->getOSMData('[out:json][timeout:25];(node["name"="parc du creux de l'enfer"]({{box}});way["name"="parc du creux de l'enfer"]({{box}});(relation["name"="parc du creux de l'enfer"]({{box}}));out center;', $radiusPlayable);

$position = $server->getPositionRequest();




$minDist = PNUtil::geoDistanceMeters($radiusPlayable);

if ($server->hasEntries()) {
    foreach($server->getEntries() as $key => $row) {
        if (isset($row["tags"]) && isset($row["tags"]["name"]) && $row["tags"]["name"] == "parc du creux de l'enfer") {
            $loc = addParc($row, $server);
            $dist = PNUtil::distance($position, $loc);
            if ($dist < $minDist)
                $minDist = $dist;
        }
    }

}

if ($minDist->meters() >= $radiusPlayable) {
    $message = PigeonNelsonMessage::makeTxtMessage("Vous n'êtes pas à côté du parc du creux de l'enfer.", "fr");
    $message->setPriority(0);
    $server->addMessage($message);
}
    
$server->printMessages();

?>
