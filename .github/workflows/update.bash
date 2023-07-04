#!/bin/bash

# The update URL is passed as the first argument to the script.
update_url=$1

echo "Updating..."

wget -O profedit-new.jar $update_url

if [ -f profedit-new.jar ]; then
    mv profedit-new.jar profedit.jar
    echo "Update completed!"
    java -jar profedit.jar
else
    echo "Update failed! Please manually download the update from the following URL:"
    echo "https://github.com/JAremko/profedit/tags"
    read -p "Press enter to continue"
fi
