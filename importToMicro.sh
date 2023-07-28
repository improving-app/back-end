#!/bin/bash

import_to_micro() {
  find . -name "$1.tar" -delete
  docker save $1\:latest > $1.tar
  multipass transfer $1.tar microk8s-vm:/tmp/$1.tar
  microk8s ctr image import /tmp/$1.tar
}
echo "Importing $1 to Microk8s"
import_to_micro $1