#Csync Dataviewer

The csync dataviewer visualizes the CSync hierarchy.

###Accessing the Dataviewer

The CSync Dataviewer is available on the same host and port that the CSync instance is run. For example, if you are running CSync locally on port 6005, the dataviewer is available on `localhost:6005`


###Using the Dataviewer

```Add Node```
To add a node, select a parent node and click the `Add Node` button on the right hand side. This will add a new node under the currently selected node and the name will be highlighted for you to type in. After typing in a new name for the node, hit enter to finish adding.

Our naming conventions do not let you have spaces or any special characters in the node name. For example, "Hello There" and "hello!!!" will not work; "helloThere" will work. Also you will not be allowed to add a node name that currently already exists for that parent, so you will be prompted to change the name if that is the case.
**Note:** implementation for adding a root node is in progress

```Delete Node```
Select a node be be deleted and press the `Delete Node` button. **Note:** we only allow deleting leaf nodes.

```Modify Node data```
When a node is selected, the data associated with it (if any) will be shown in the Properties panel on the right. Click the `Edit` button on the right top corner to add/modify data for that node. Click `Save` to save. If you click away from the Properties section without saving, you will be prompted to finish editing, then continue.


### Dependency Table

| Module Name | Publisher | Date Published | Version | GitHub | License |
|:------------| ----------| ---------------| --------| -------| -------:|
| jquery | timmywil | 2016-09-22T22:32:49.360Z | 3.1.1 | [github.com/jquery/jquery](https://github.com/jquery/jquery) | [MIT](http://spdx.org/licenses/MIT) |
| gulp-minify-css | murphydanger | 2016-02-24T10:25:03.149Z | 1.2.4 |  | [MIT](http://spdx.org/licenses/MIT) |
| knockout | mbest | 2016-11-08T07:13:32.816Z | 3.4.1 | [github.com/knockout/knockout](https://github.com/knockout/knockout) | [MIT](http://spdx.org/licenses/MIT) |
| split.js | nathancahill | 2016-12-29T17:56:16.783Z | 1.2.0 | [github.com/nathancahill/Split.js](https://github.com/nathancahill/Split.js) | [MIT](http://spdx.org/licenses/MIT) |
| jstree | vakata | 2016-10-31T09:53:31.253Z | 3.3.3 | [github.com/vakata/jstree](https://github.com/vakata/jstree) | [MIT](http://spdx.org/licenses/MIT) |
| gulp | phated | 2016-02-08T18:50:16.472Z | 3.9.1 | [github.com/gulpjs/gulp](https://github.com/gulpjs/gulp) | [MIT](http://spdx.org/licenses/MIT) |
| gulp-concat | contra | 2016-11-13T18:53:13.734Z | 2.6.1 | [github.com/contra/gulp-concat](https://github.com/contra/gulp-concat) | [MIT](http://spdx.org/licenses/MIT) |
| webworkify | anandthakker | 2016-09-07T15:16:32.281Z | 1.4.0 | [github.com/substack/webworkify](https://github.com/substack/webworkify) | [MIT](http://spdx.org/licenses/MIT) |
| mustache | dasilvacontin | 2016-11-08T16:25:18.753Z | 2.3.0 | [github.com/janl/mustache.js](https://github.com/janl/mustache.js) | [MIT](http://spdx.org/licenses/MIT) |
| gulp-streamify | nfroidure | 2015-09-07T10:30:56.550Z | 1.0.2 | [github.com/nfroidure/gulp-streamify](https://github.com/nfroidure/gulp-streamify) | [MIT](http://spdx.org/licenses/MIT) |
| vinyl-source-stream | hughsk | 2015-03-06T06:41:43.495Z | 1.1.0 | [github.com/hughsk/vinyl-source-stream](https://github.com/hughsk/vinyl-source-stream) | [MIT](http://spdx.org/licenses/MIT) |
| gulp-uglify | terinjokes | 2016-08-01T21:55:41.164Z | 2.0.0 | [github.com/terinjokes/gulp-uglify](https://github.com/terinjokes/gulp-uglify) | [MIT](http://spdx.org/licenses/MIT) |
| gulp-cli | phated | 2016-07-15T17:58:18.086Z | 1.2.2 | [github.com/gulpjs/gulp-cli](https://github.com/gulpjs/gulp-cli) | [MIT](http://spdx.org/licenses/MIT) |
| grunt | shama | 2016-04-05T18:16:49.769Z | 1.0.1 | [github.com/gruntjs/grunt](https://github.com/gruntjs/grunt) | [MIT](http://spdx.org/licenses/MIT) |
| browserify | substack | 2017-01-04T08:10:34.289Z | 13.3.0 | [github.com/substack/node-browserify](https://github.com/substack/node-browserify) | [MIT](http://spdx.org/licenses/MIT) |
