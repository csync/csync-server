'use strict';
var express = require('express');
var bodyParser = require('body-parser');
var csync = require('csync');
var request = require('request');

// create a new express server
var app = express();
app.use(bodyParser.json()); // for parsing application/json

// CSync
var csyncApp = csync({ host: "localhost", port: 6005, useSSL: false });
var keys = {};
csyncApp.authenticate("demo", "demoToken");


// render index page
app.put('/webhooks', function (req, res) {
  console.log("Put Request: ", req.body);
  var url = req.body.url;
  var csyncKey = csyncApp.key(req.body.key);
  keys[req.body.key] = csyncKey;
  csyncKey.listen(function (error, value) {
    if (error) {
      // handle error
      console.log("Error: ", error);
    } else {
      // value has key, data, acl, exists
      request({
        method: "POST",
        uri: url,
        body: value,
        headers: {
          "Authorization": "Basic YzU1YWZjMDUtOTc3My00ODk0LTgyMWQtMGI4NjBjYTU1MjRmOjB5QXRaZm51dGJMY2U3TzRoTnNYNmFBWVJVM25zRXNNam1SbFBTcE9xeUNsUHZtN3JLVzQ4b0owZ3Z4ZWZGbDI=",
          "Content-Type": "application/json"
        },
        json: true
      },
        function (error, response, body) {
          console.log("Response Body: ", body);
        });
    }
  });
  res.sendStatus(200);
});

app.delete('/webhooks', function (req, res) {
  console.log("Delete Request: ", req.body);
  var url = req.body.url;
  var csyncKey = keys[req.body.key];
  if (!csyncKey) {
    res.sendStatus(404);
  }
  csyncKey.unlisten();
  delete keys[req.body.key];
  res.sendStatus(200);
});

// start server on the specified port and binding host
app.listen(6004, '0.0.0.0', function () {
  console.log("server started");
});