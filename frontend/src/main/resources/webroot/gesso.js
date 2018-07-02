/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

"use strict";

const $ = document.querySelector.bind(document);
const $$ = document.querySelectorAll.bind(document);

Element.prototype.$ = function () {
  return this.querySelector.apply(this, arguments);
};

Element.prototype.$$ = function () {
  return this.querySelectorAll.apply(this, arguments);
};

const gesso = {
    openRequest: function (method, url, handler) {
        let request = new XMLHttpRequest();

        request.open(method, url);

        if (handler != null) {
            request.addEventListener("load", handler);
        }

        return request;
    },

    minFetchInterval: 500,
    maxFetchInterval: 60 * 1000,
    fetchStates: {}, // By path

    FetchState: function () {
        return {
            currentInterval: null,
            currentTimeoutId: null,
            failedAttempts: 0,
            etag: null,
            timestamp: null
        }
    },

    getFetchState: function (path) {
        let state = gesso.fetchStates[path];

        if (state == null) {
            state = new gesso.FetchState();
            gesso.fetchStates[path] = state;
        }

        return state;
    },

    fetch: function (path, dataHandler) {
        console.log("Fetching data from", path);

        let state = gesso.getFetchState(path);

        function loadHandler(event) {
            if (event.target.status === 200) {
                state.failedAttempts = 0;
                state.etag = event.target.getResponseHeader("ETag");

                dataHandler(JSON.parse(event.target.responseText));
            } else if (event.target.status == 304) {
                state.failedAttempts = 0;
            }

            state.timestamp = new Date().getTime();
        }

        function errorHandler(event) {
            console.log("Fetch failed");

            state.failedAttempts++;
        }

        let request = gesso.openRequest("GET", path, loadHandler);

        request.addEventListener("error", errorHandler);

        let etag = state.etag;

        if (etag) {
            request.setRequestHeader("If-None-Match", etag);
        }

        request.send();

        return state;
    },

    fetchPeriodically: function (path, dataHandler) {
        let state = gesso.getFetchState(path);

        window.clearTimeout(state.currentTimeoutId);
        state.currentInterval = gesso.minFetchInterval;

        gesso.doFetchPeriodically(path, dataHandler, state);

        return state;
    },

    doFetchPeriodically: function (path, dataHandler, state) {
        if (state.currentInterval >= gesso.maxFetchInterval) {
            window.setInterval(gesso.fetch, gesso.maxFetchInterval, path, dataHandler);
            return;
        }

        state.currentTimeoutId = window.setTimeout(gesso.doFetchPeriodically,
                                                   state.currentInterval,
                                                   path, dataHandler, state);

        state.currentInterval = Math.min(state.currentInterval * 2, gesso.maxFetchInterval);

        gesso.fetch(path, dataHandler);
    },

    parseQueryString: function (str) {
        if (str.startsWith("?")) {
            str = str.slice(1);
        }

        let qvars = str.split(/[&;]/);
        let obj = {};

        for (let i = 0; i < qvars.length; i++) {
            let [name, value] = qvars[i].split("=", 2);

            name = decodeURIComponent(name);
            value = decodeURIComponent(value);

            obj[name] = value;
        }

        return obj;
    },

    emitQueryString: function (obj) {
        let tokens = [];

        for (let name in obj) {
            if (!obj.hasOwnProperty(name)) {
                continue;
            }

            let value = obj[name];

            name = decodeURIComponent(name);
            value = decodeURIComponent(value);

            tokens.push(name + "=" + value);
        }

        return tokens.join(";");
    },

    createElement: function (parent, tag, text) {
        let elem = document.createElement(tag);

        if (parent != null) {
            parent.appendChild(elem);
        }

        if (text != null) {
            gesso.createText(elem, text);
        }

        return elem;
    },

    createText: function (parent, text) {
        let node = document.createTextNode(text);

        if (parent != null) {
            parent.appendChild(node);
        }

        return node;
    },

    _setSelector: function (elem, selector) {
        if (selector == null) {
            return;
        }

        if (selector.startsWith("#")) {
            elem.setAttribute("id", selector.slice(1));
        } else {
            elem.setAttribute("class", selector);
        }
    },

    createDiv: function (parent, selector, text) {
        let elem = gesso.createElement(parent, "div", text);

        gesso._setSelector(elem, selector);

        return elem;
    },

    createSpan: function (parent, selector, text) {
        let elem = gesso.createElement(parent, "span", text);

        gesso._setSelector(elem, selector);

        return elem;
    },

    createLink: function (parent, href, text) {
        let elem = gesso.createElement(parent, "a", text);

        if (href != null) {
            elem.setAttribute("href", href);
        }

        return elem;
    },

    createTable: function (parent, headings, rows) {
        let elem = gesso.createElement(parent, "table");
        let thead = gesso.createElement(elem, "thead");
        let tbody = gesso.createElement(elem, "tbody");

        if (headings) {
            let tr = gesso.createElement(thead, "tr");

            for (let heading of headings) {
                gesso.createElement(tr, "th", heading);
            }
        }

        for (let row of rows) {
            let tr = gesso.createElement(tbody, "tr");

            for (let cell of row) {
                gesso.createElement(tr, "td", cell);
            }
        }

        return elem;
    },

    replaceElement: function(oldElement, newElement) {
        oldElement.parentNode.replaceChild(newElement, oldElement);
    },

    formatDuration: function (milliseconds) {
        if (milliseconds == null) {
            return "-";
        }

        let seconds = Math.round(milliseconds / 1000);
        let minutes = Math.round(milliseconds / 60 / 1000);
        let hours = Math.round(milliseconds / 3600 / 1000);
        let days = Math.round(milliseconds / 86400 / 1000);
        let weeks = Math.round(milliseconds / 432000 / 1000);

        if (weeks >= 2) {
            return `${weeks} weeks`;
        }

        if (days >= 2) {
            return `${days} days`;
        }

        if (hours >= 1) {
            return `${hours} hours`;
        }

        if (minutes >= 1) {
            return `${minutes} minutes`;
        }

        if (seconds === 1) {
            return "1 second";
        }

        return `${seconds} seconds`;
    }
}
