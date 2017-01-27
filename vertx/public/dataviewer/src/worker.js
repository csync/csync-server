/*
 * Copyright IBM Corporation 2016-2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

var csync =  require('csync');
var config = require('../config');

var nodeCache = [];
var MAX_DATA_POINTS = 30; 
var csyncInstance;

module.exports = function(self){
    self.addEventListener('message', function(event){
        switch(event.data.type){
            case "set_host_port":                
                createCsyncInstance(event.data);
                break;
            case "new_node":
                writeUpdateNode(event.data, {}, self);
                break;
            case "delete_node":
                deleteNode(event.data, self);
                break;
            case "update_data":
                writeUpdateNode(event.data, JSON.parse(event.data.data), self);
                break;
            default:
                listenToCsync(self);
        }
    });
};

function writeUpdateNode(obj, data, self){
    var keyToListen = csyncInstance.key(obj.key);
    keyToListen.write(data)
        .then(function(val){
            console.log(val);
            self.postMessage(obj);
        })
        .catch(function(err){
            console.log(err)
            obj.error = err.message;
            self.postMessage(obj);
        });
}

function deleteNode(data, self){
    var keyToListen = csyncInstance.key(data.key);
    keyToListen.delete()
        .then(function(val){
            console.log(val);
            self.postMessage(data);
        })
        .catch(function(err){
            console.log(err);
            data.error = err.message;
            self.postMessage(data);
        });
}

function listenToCsync(self){
    var init = 0;
    var keyToListen = csyncInstance.key(config.pathToListen);
    keyToListen.listen(function(error, incomingData){
        nodeCache.push(incomingData);
        setTimeout(function(){
            for(var i=0; init <= MAX_DATA_POINTS; i++){
                if(nodeCache.length === 0){
                    return;
                }
                self.postMessage(nodeCache.pop());
            }
        }, 500);
    });
}

function createCsyncInstance(data){
    var host = data.host;
    var port = data.port;
    var ssl = (data.protocol !== "http:");
    console.log("Data: ", data);
    if(data.host === ""){
        host = config.csyncHost;
        port = config.csyncPort;
    }
    if(data.port === "") {
        if(!ssl){
            port = 80;
        }
        else{
            port = 443;
        }
    }
    var csyncInfo = { host: host, port: port, useSSL : ssl };
    csyncInstance = csync(csyncInfo);
    csyncInstance.authenticate(data.provider, data.token);

}