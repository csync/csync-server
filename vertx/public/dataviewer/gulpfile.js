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

var gulp = require('gulp');
var	browserify = require('browserify');
var source = require('vinyl-source-stream')
var streamify = require('gulp-streamify')
var concat = require('gulp-concat');
var minifyCSS = require('gulp-minify-css');
var uglify = require('gulp-uglify');

gulp.task('default', function(){
	gulp.start('build');
});

gulp.task('package', function(){
	return browserify({entries: './src/dataModel.js', debug: true})
		.bundle()
		.pipe(source('bundle.js'))
		.pipe(streamify(uglify()))
		.pipe(gulp.dest('./ui'));
});

gulp.task('css', function(){
	return gulp.src(['./node_modules/jstree/dist/themes/default/style.min.css', './ui/style.css'])
	    .pipe(minifyCSS())
	    .pipe(concat('bundle.css'))
	    .pipe(gulp.dest('./ui'));
});

gulp.task('assets', function(){
	return gulp.src(['./node_modules/jstree/dist/themes/default/*.png', './node_modules/jstree/dist/themes/default/*.gif'])
		.pipe(gulp.dest('./ui'));
});

gulp.task('build', ['package', 'css', 'assets'], function() {
	process.exit(0);
});
