#!/bin/bash

if [[ $HMV_FILENAME == tradeaudit-server-assembly-* ]] ; then
    echo "Copying $MVN_FILENAME to /home/tradeaudit"
    sudo -- mv $HMV_FILENAME /home/tradeaudit/
    sudo -- chown tradeaudit:tradeaudit /home/tradeaudit/$HMV_FILENAME
    echo "Linking current version of tradeaudit-server-assembly.jar"
    sudo -- rm /home/tradeaudit/tradeaudit-server-assembly.jar
    sudo -- ln -s /home/tradeaudit/$HMV_FILENAME /home/tradeaudit/tradeaudit-server-assembly.jar
    echo "Restarting tradeaudit service"
    sudo -- systemctl restart tradeaudit
    echo "Successfully installed new tradeaudit version $HMV_FILENAME"
fi

if [[ $HMV_FILENAME == tradeaudit-scraper-assembly-* ]] ; then
    echo "Copying $MVN_FILENAME to /home/scraper"
    sudo -- mv $HMV_FILENAME /home/scraper/
    sudo -- chown scraper:scraper /home/scraper/$HMV_FILENAME
    echo "Linking current version of tradeaudit-scraper-assembly.jar"
    sudo -- rm /home/scraper/tradeaudit-scraper-assembly.jar
    sudo -- ln -s /home/scraper/$HMV_FILENAME /home/scraper/tradeaudit-scraper-assembly.jar
    echo "Restarting scraper service"
    sudo -- systemctl restart scraper
    echo "Successfully installed new scraper version $HMV_FILENAME"
fi

if [[ $HMV_FILENAME == build-action-file-receiver-assembly-* ]] ; then
    echo "Linking current version of build-action-file-receiver-assembly.jar"
    sudo -- rm ../build-action-file-receiver-assembly.jar
    sudo -- ln -s files/$HMV_FILENAME ../build-action-file-receiver-assembly.jar
    echo "Successfully installed new $HMV_FILENAME"
fi

if [[ $HMV_FILENAME == build-action-file-receiver-graal-linux-* ]] ; then
    echo "Linking current version of build-action-file-receiver-graal-linux"
    sudo -- rm ../build-action-file-receiver-graal-linux
    sudo -- ln -s files/$HMV_FILENAME ../build-action-file-receiver-graal-linux
    sudo -- chmod +x ../build-action-file-receiver-graal-linux
    echo "Successfully installed new $HMV_FILENAME"
fi
