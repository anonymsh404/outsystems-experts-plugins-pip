#!/usr/bin/env node

module.exports = function (context) {
    let fs = require('fs');
    let path = require('path');
    let Q;
    try {
        Q = require('q');
    } catch (e) {
        Q = global.Q;
    }
    let deferral = Q ? Q.defer() : Promise.defer ? Promise.defer() : (() => {
        let resolve, reject;
        let p = new Promise((res, rej) => { resolve = res; reject = rej; });
        return { promise: p, resolve: resolve, reject: reject };
    })();

    // android platform directory
    let platformAndroidDir = path.join(context.opts.projectRoot, 'platforms/android');
    let androidManifestFile = path.join(platformAndroidDir, 'AndroidManifest.xml');

    function changeProperty(inputData, propertyName, targetValue, merge) {
        var hasProperty = (inputData.indexOf(propertyName) > -1);
        var propertyVal = inputData;

        if (hasProperty) {
            if (merge) {
            } else {
                propertyVal = inputData.replace(new RegExp("(.*" + propertyName + "=\")([\\w\\|]+)\"", ""), "$1" + targetValue + "\"");
            }
        } else {
            propertyVal = inputData.replace(/>$/," android:" + propertyName + "=\"" + targetValue + "\" >");
        }
        return propertyVal;
    }

    if (fs.existsSync(androidManifestFile)) {
        fs.readFile(androidManifestFile, 'UTF-8', function (err, data) {
            if (err) {
                deferral.reject(err);
                return;
            }
            var mainActReg = /<activity.+name="MainActivity".+>/;
            var matchResult = data.match(mainActReg);
            
            if (!matchResult) {
                deferral.resolve();
                return;
            }

            var actString = matchResult[0];

            var test = changeProperty(actString, "configChanges", "orientation|keyboardHidden|keyboard|screenSize|locale", true);
            test = changeProperty(test, "supportsPictureInPicture", "true", false);
            test = changeProperty(test, "resizeableActivity", "true", false);

            var finalData = data.replace(mainActReg, test);

            fs.writeFile(androidManifestFile, finalData, 'UTF-8', function (err) {
                if (err) {
                    deferral.reject(err);
                } else {
                    deferral.resolve();
                }
            });
        });
    } else {
        deferral.resolve();
    }

    return deferral.promise || deferral;
};
