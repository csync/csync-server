#!/bin/bash

#
# Copyright IBM Corporation 2016-2017
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

echo "Starting RABBITMQ"
#work around to avoid error when restarting rabbit: invoke-rc.d: policy-rc.d denied execution of start.
sed -i -e 's/101/0/g' /usr/sbin/policy-rc.d
/docker-entrypoint-rabbit.sh rabbitmq-server &
sleep 5
rabbitmq-plugins enable rabbitmq_management
rabbitmqctl stop
invoke-rc.d rabbitmq-server start
sleep 5
echo "Finished starting RABBITMQ"

find / -name rabbitmqctl -print
if [ -z $CSYNC_RABBITMQ_PASSWORD ]; then
    echo "CSYNC_RABBITMQ_PASSWORD not specified, using default value"
    export CSYNC_RABBITMQ_PASSWORD=guest
fi
if [ -z $CSYNC_RABBITMQ_USER ]; then
    echo "CSYNC_RABBITMQ_USER not specified, using default value"
    export CSYNC_RABBITMQ_USER=guest
fi

rabbitmqctl add_user admin admin
rabbitmqctl set_user_tags admin administrator
rabbitmqctl add_user $CSYNC_RABBITMQ_USER $CSYNC_RABBITMQ_PASSWORD
rabbitmqctl add_vhost csync
rabbitmqctl set_permissions -p csync $CSYNC_RABBITMQ_USER ".*" ".*" ".*"
rabbitmqctl set_permissions -p csync admin ".*" ".*" ".*"

echo "Starting postgres"
export LANG=en_US.utf8
/docker-entrypoint-postgres.sh postgres &
sleep 7
echo "Finished starting postgres"
ls -l /var/lib/postgresql/data

rm -f /opt/docker/RUNNING_PID

sed -i "s/YOUR_GOOGLE_CLIENT_ID_HERE/"$CSYNC_GOOGLE_CLIENT_IDS"/" /opt/docker/public/dataviewer/ui/bundle.js

echo "Starting CSync"
export USER=postgres
export VCAP_APPLICATION="{\"space_id\":\"$space_id\", \"application_id\":\"$uuid\"}"
su -m postgres -c /opt/docker/bin/csync

