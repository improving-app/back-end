#!/bin/bash

services=("improving-app-gateway
improving-app-tenant
improving-app-organization
improving-app-member
improving-app-store
improving-app-event
improving-app-product")

#enable if you want to clear your microk8s registry of old service images

#echo "${services[@]}" | xargs -n 1 -P 7 ./cleanMicrok8sImages.sh

echo "${services[@]}" | xargs -n 1 -P 7 ./importToMicro.sh



