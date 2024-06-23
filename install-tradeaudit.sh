#!/bin/bash

if [[ $HMV_FILENAME == tradeauditserver-assembly-* ]] ; then
    echo "sudo -- mv $HMV_FILENAME /home/tradeaudit/"
    echo "sudo -- rm /home/tradeaudit/tradeauditserver-assembly.jar"
    echo "sudo -- ln -s /home/tradeaudit/$HMV_FILENAME /home/tradeaudit/tradeauditserver-assembly.jar"
    echo "sudo -- systemctl restart tradeaudit"
    echo "Successfully installed new tradeaudit version $HMV_FILENAME"
fi