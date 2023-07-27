#!/bin/bash

#note: this line is flaky and may generate a header that will break the script - run from command line
# microk8s ctr images ls name~=improving-app | awk {'print $1'} > image_ls

#check image_ls file for the header or other nonsense to be removed

<image_ls xargs -n 1 -P 25 microk8s ctr images rm