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

"use strict";
var webworkify = require('webworkify');
var Split = require('split.js');
var worker = webworkify(require('./worker.js'));
var ko = require('knockout');
var $ = require('jquery');
var jstree = require('jstree');
var TreeModel = require('./TreeModel.js');
var PropertyViewModel = require('./PropertyViewModel.js');
var config = require('../config');

//UI Elements
var jsTreeElem = $("#jstree");
var treeElem = $(".content");
var deleteButtonElem = $("#deleteButton");
var host = window.location.hostname;
var port = window.location.port;
var protocol = window.location.protocol;

//start worker 'thread'
var initInfo = {
    host: host,
    port: port,
    protocol: protocol,
    type: "set_host_port"
};

var shouter = new ko.subscribable();
var tree = new TreeModel(shouter, worker);
var propertyView = new PropertyViewModel(tree, shouter, worker);
var meta = document.createElement('meta');
var modal = document.getElementById('authModal');

meta.name = "google-signin-client_id";
meta.content = config.googleClientId;
document.getElementsByTagName('head')[0].appendChild(meta);

worker.onerror = function(event){
    throw new Error(event.message + " (" + event.filename + ":" + event.lineno + ")");
};

var editCallback = function(node, status, cancelled){
    if(node.text === "Node Name"){
        showHideSnackbar("This node already exists: enter a Node Name", true);
        jsTreeElem.jstree(true).edit(node.id, "Node Name", editCallback);
    }
};

window.onSignIn =function(googleUser){
    var modal = document.getElementById('authModal');
    modal.style.display = "none";
    var profile = googleUser.getBasicProfile();
    initInfo.token = googleUser.getAuthResponse().id_token;
    initInfo.user = profile.getEmail();
    initInfo.provider = config.provider;
    worker.postMessage(initInfo);
    worker.postMessage("connect");
}

$(".guest-login").click(function(){
    initInfo.token = config.csyncDemoToken;
    initInfo.provider = config.demoProvider;
    worker.postMessage(initInfo);
    worker.postMessage("connect");
    modal.style.display = "none";
});

worker.addEventListener('message', function(event){
    var data = event.data;
    switch(data.type){
            case "new_node":
                feedback(data, "Successfully saved node \"" + data.text + "\" to csync");
                if(!!data.error){
                    jsTreeElem.jstree(true).edit(data.key, "Node Name", editCallback);
                }
                $("#addNode").prop("disabled", false);
                break;
            case "delete_node":
                feedback(data, "Successfully deleted node \""+ data.text+"\" from csync");
                break;
            case "update_data":
                feedback(data, "Successfully saved data for node \""+ data.text+"\" to csync");
                break;
            default:
                processData(data);
    }
});

function feedback(data, message){
    if(!!data.error){
        showHideSnackbar(data.error, true);
        return;
    }
    showHideSnackbar(message, false);
}

function showHideSnackbar(message, isError){
    var notifBar = $("#notif-bar");
    if(isError){
        notifBar[0].style.backgroundColor = "#E44C4C";
    }else{
        notifBar[0].style.backgroundColor = "#62D295";
    }
    notifBar.text(message);
    notifBar.stop(true, false).fadeIn(400);
    notifBar.delay(3000).fadeOut(1000);
}

function processData(incomingData){
    if (incomingData.exists) {
        var node = jsTreeElem.jstree().get_node(incomingData.key);
        if (!node){
            tree.createAndAddNode(incomingData);
        }
        else{
            tree.updateNodeData(incomingData);
        }
    }
    else {
        tree.deleteNode(incomingData);
    }
}

//Adds resize functionality to the section splitter
Split(['#middle', '#sidebar-right'], {
    sizes: [75, 25],
    direction: 'horizontal',
    gutterSize: 3,
    cursor: 'col-resize'
});

function setupJSTree(){
    $("#jstree")
        .jstree({
            "core": {
                "multiple": false,
                "check_callback" : true,
                "themes": {
                    "theme": "default",
                    "url": "./ui/bundle.css",
                    "icons": false
                }
            },
            "plugins": [ "state", "unique" ]
        })
        .bind("rename_node.jstree", function (event, data) {
            shouter.notifySubscribers(data.node, "afterRename");
        })
        .on('select_node.jstree', function(event, data){
            if (data.instance.is_leaf(data.node)) {
                deleteButtonElem.prop("disabled", false);
            }
            else {
                deleteButtonElem.prop("disabled", true);
            }
            tree.selectedNode(data.node);
        });
}

ko.applyBindings(tree, treeElem[0]);
ko.applyBindings(propertyView, $("#sidebar-right")[0]);
setupJSTree();