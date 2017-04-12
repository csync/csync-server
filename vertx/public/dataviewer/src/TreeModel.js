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

var ko = require('knockout');
var $ = require('jquery');
var jstreeState = localStorage.getItem('jstree');
var tree = $('#jstree');

module.exports = function(shouter, worker) {
    this.selectedNode = ko.observable({});

    //subscribe to addNode
    shouter.subscribe(function (data) {
        data.data = {};
        data.acl = "$publicCreate";
        var id;
        if(this.selectedNode() === null || Object.keys(this.selectedNode()).length === 0){
            id = tree.jstree("create_node", "#", data, "last");
        }
        else{
            id = tree.jstree("create_node", this.selectedNode(), data, "last");
        }
        tree.jstree('open_node', this.selectedNode());
        tree.jstree(true).edit(id, "Node Name");
    }, this, "addNode");

    //subscribe to rename binding (jstree)
    shouter.subscribe(function (node) {
        var csyncKey;
        var newId;
        if(node.parent === "#"){
            newId = node.text;
        }
        else{
            newId = node.parent + "." + node.text;
        }
        tree.jstree().set_id(node, newId);
        //ensure sorted after add
        var parentNode = tree.jstree().get_node(node.parent);
        var position = getPosition(parentNode.children || [], node.id);
        tree.jstree().move_node(node, parentNode, position);
        this.deselectAll();
        tree.jstree().select_node(newId);
        var writeObj = {
            key: this.selectedNode().id,
            type: "new_node",
            text: node.text,
            parent: node.parent
        };
        // send worker a write task
        worker.postMessage(writeObj);
    }, this, "afterRename");
    
    this.deleteNode = function(incomingData){
        //set selected node to parent
        var currNode = tree.jstree().get_node(incomingData.key);
        tree.jstree().delete_node(currNode);  
        var jstreeChildren = $(".jstree-children");
        if(this.selectedNode() !== null && jstreeChildren.has("li").length && incomingData.key === this.selectedNode().id){
            var parentID = this.selectedNode().parent;
            var parentNode = tree.jstree().get_node(parentID);
            tree.jstree().deselect_all(true);
            tree.jstree().select_node(parentNode);
            this.deleteNonExsistentParents(parentNode);
        }
        else{
            if(!jstreeChildren.has("li").length){
                this.selectedNode(null);
            }
        }
    }

    //update data of existing node
    this.updateNodeData = function(incomingData){
        //TODO csync writes parent node with undefined data
        if(incomingData.data === undefined){
            return;
        }
        var node = tree.jstree().get_node(incomingData.key);
        tree.jstree().get_node(incomingData.key).data = incomingData.data; 
        tree.jstree().get_node(incomingData.key).original.acl = incomingData.acl; 
        tree.jstree().get_node(incomingData.key).original.status = "valid";
        if (tree.jstree().get_selected()[0] === incomingData.key) {
            //publish to update Properties
            this.selectedNode(node);
        }
    }

    this.createAndAddNode = function(incomingData){
        var position = -1;
        var keyArray = (incomingData.key).split(".");
        //check if parents exist and create if not
        for (var i = 0; i < keyArray.length; i++) {
            var node = {};
            node.id = keyArray.slice(0, i+1).join(".");
            node.status = "invalid";
            node.acl = incomingData.acl;
            var curNode = tree.jstree().get_node(node.id);
            if (!curNode){
                node.text = keyArray[i];
                node.parent = (keyArray.slice(0, i)).join(".");
                node.data = {};
                if(i === keyArray.length-1){
                    node.status = "valid";
                    node.data = incomingData.data;
                }
                if(i === 0){
                    node.parent = "#";
                    position = getPosition(getRootNodes(), node.id);
                    tree.jstree().create_node(node.parent, node, position);                    
                }
                else {
                    var parentNode = tree.jstree().get_node(node.parent);
                    position = getPosition(parentNode.children || [], node.id);
                    tree.jstree().create_node(parentNode, node, position);
                }
                this.restoreState(node);                    
            }
        }
    }

    this.deleteNonExsistentParents = function(node){
        var tempNode = node;
        while(tempNode.id != "#"){
            var parent = tree.jstree().get_node(tempNode.parent);
            if(tempNode.original.status != "valid" && tempNode.children.length == 0){
                tree.jstree().delete_node(tempNode);
                tempNode = parent;
                if(tempNode.parent === null){
                    this.selectedNode(null);
                }
                else{
                    tree.jstree().deselect_all(true);
                    tree.jstree().select_node(tempNode);
                }
            }
            else {
                return;
            }
        }
    }

    function getPosition(siblings, nodeId){
        var lo = 0;
        var hi = siblings.length - 1; 
        while(lo <= hi ){
            var mid = (lo + (hi - lo) / 2) >> 0;
            if      (nodeId < siblings[mid]) hi = mid - 1;
            else if (nodeId > siblings[mid]) lo = mid + 1;
            else return mid;
        }
        return lo;
    }

    function getRootNodes(){
        var siblings = tree.jstree().get_children_dom("#");
        var nodes = [];
        for(var i=0; i<siblings.length; i++){
            nodes.push(siblings[i].id);
        }
        return nodes;
    }

    this.restoreState = function(node){
        if(jstreeState === null || jstreeState === undefined){
            return;
        }
        var jstreeStateObj = JSON.parse(jstreeState);
        var stateCore = jstreeStateObj.state.core;
        var openIndex = stateCore.open.indexOf(node.parent);
        //check if open
        if(openIndex !== -1){
            tree.jstree().open_node(node.parent);
        }
        //check if selected
        if(stateCore.selected[0] == node.id){
            tree.jstree().select_node(node.id);
            if(openIndex === -1){
                tree.jstree().close_node(node.parent);
            }
        }

        if(stateCore.selected.length === 0){
            this.selectedNode(null);
        }
    }

    this.deselectAll = function(){
        tree.jstree().deselect_all(true);
    }
    
}