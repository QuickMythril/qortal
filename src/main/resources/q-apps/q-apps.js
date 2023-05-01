function httpGet(url) {
    var request = new XMLHttpRequest();
    request.open("GET", url, false);
    request.send(null);
    return request.responseText;
}

function httpGetAsyncWithEvent(event, url) {
    fetch(url)
        .then((response) => response.text())
        .then((responseText) => {

            if (responseText == null) {
                // Pass to parent (UI), in case they can fulfil this request
                event.data.requestedHandler = "UI";
                parent.postMessage(event.data, '*', [event.ports[0]]);
                return;
            }

            handleResponse(event, responseText);

        })
        .catch((error) => {
            let res = {};
            res.error = error;
            handleResponse(event, JSON.stringify(res));
        })
}

function handleResponse(event, response) {
    if (event == null) {
        return;
    }

    // Handle empty or missing responses
    if (response == null || response.length == 0) {
        response = "{\"error\": \"Empty response\"}"
    }

    // Parse response
    let responseObj;
    try {
        responseObj = JSON.parse(response);
    } catch (e) {
        // Not all responses will be JSON
        responseObj = response;
    }

    // GET_QDN_RESOURCE_URL has custom handling
    const data = event.data;
    if (data.action == "GET_QDN_RESOURCE_URL") {
        if (responseObj == null || responseObj.status == null || responseObj.status == "NOT_PUBLISHED") {
            responseObj = {};
            responseObj.error = "Resource does not exist";
        }
        else {
            responseObj = buildResourceUrl(data.service, data.name, data.identifier, data.path, false);
        }
    }

    // Respond to app
    if (responseObj.error != null) {
        event.ports[0].postMessage({
            result: null,
            error: responseObj
        });
    }
    else {
        event.ports[0].postMessage({
            result: responseObj,
            error: null
        });
    }
}

function buildResourceUrl(service, name, identifier, path, isLink) {
    if (isLink == false) {
        // If this URL isn't being used as a link, then we need to fetch the data
        // synchronously, instead of showing the loading screen.
        url = "/arbitrary/" + service + "/" + name;
        if (identifier != null) url = url.concat("/" + identifier);
        if (path != null) url = url.concat("?filepath=" + path);
    }
    else if (_qdnContext == "render") {
        url = "/render/" + service + "/" + name;
        if (path != null) url = url.concat((path.startsWith("/") ? "" : "/") + path);
        if (identifier != null) url = url.concat("?identifier=" + identifier);
    }
    else if (_qdnContext == "gateway") {
        url = "/" + service + "/" + name;
        if (identifier != null) url = url.concat("/" + identifier);
        if (path != null) url = url.concat((path.startsWith("/") ? "" : "/") + path);
    }
    else {
        // domainMap only serves websites right now
        url = "/" + name;
        if (path != null) url = url.concat((path.startsWith("/") ? "" : "/") + path);
    }

    if (isLink) url = url.concat((url.includes("?") ? "" : "?") + "&theme=" + _qdnTheme);

    return url;
}

function extractComponents(url) {
    if (!url.startsWith("qortal://")) {
        return null;
    }

    url = url.replace(/^(qortal\:\/\/)/,"");
    if (url.includes("/")) {
        let parts = url.split("/");
        const service = parts[0].toUpperCase();
        parts.shift();
        const name = parts[0];
        parts.shift();
        let identifier;

        if (parts.length > 0) {
            identifier = parts[0]; // Do not shift yet
            // Check if a resource exists with this service, name and identifier combination
            const url = "/arbitrary/resource/status/" + service + "/" + name + "/" + identifier;
            const response = httpGet(url);
            const responseObj = JSON.parse(response);
            if (responseObj.totalChunkCount > 0) {
                // Identifier exists, so don't include it in the path
                parts.shift();
            }
            else {
                identifier = null;
            }
        }

        const path = parts.join("/");

        const components = {};
        components["service"] = service;
        components["name"] = name;
        components["identifier"] = identifier;
        components["path"] = path;
        return components;
    }

    return null;
}

function convertToResourceUrl(url, isLink) {
    if (!url.startsWith("qortal://")) {
        return null;
    }
    const c = extractComponents(url);
    if (c == null) {
        return null;
    }

    return buildResourceUrl(c.service, c.name, c.identifier, c.path, isLink);
}

window.addEventListener("message", (event) => {
    if (event == null || event.data == null || event.data.length == 0) {
        return;
    }
    if (event.data.action == null) {
        // This could be a response from the UI
        handleResponse(event, event.data);
    }
    if (event.data.requestedHandler != null && event.data.requestedHandler === "UI") {
        // This request was destined for the UI, so ignore it
        return;
    }

    console.log("Core received event: " + JSON.stringify(event.data));

    let url;
    let data = event.data;

    switch (data.action) {
        case "GET_ACCOUNT_DATA":
            return httpGetAsyncWithEvent(event, "/addresses/" + data.address);

        case "GET_ACCOUNT_NAMES":
            return httpGetAsyncWithEvent(event, "/names/address/" + data.address);

        case "GET_NAME_DATA":
            return httpGetAsyncWithEvent(event, "/names/" + data.name);

        case "GET_QDN_RESOURCE_URL":
            // Check status first; URL is built and returned automatically after status check
            url = "/arbitrary/resource/status/" + data.service + "/" + data.name;
            if (data.identifier != null) url = url.concat("/" + data.identifier);
            return httpGetAsyncWithEvent(event, url);

        case "LINK_TO_QDN_RESOURCE":
            if (data.service == null) data.service = "WEBSITE"; // Default to WEBSITE
            window.location = buildResourceUrl(data.service, data.name, data.identifier, data.path, true);
            return;

        case "LIST_QDN_RESOURCES":
            url = "/arbitrary/resources?";
            if (data.service != null) url = url.concat("&service=" + data.service);
            if (data.name != null) url = url.concat("&name=" + data.name);
            if (data.identifier != null) url = url.concat("&identifier=" + data.identifier);
            if (data.default != null) url = url.concat("&default=" + new Boolean(data.default).toString());
            if (data.includeStatus != null) url = url.concat("&includestatus=" + new Boolean(data.includeStatus).toString());
            if (data.includeMetadata != null) url = url.concat("&includemetadata=" + new Boolean(data.includeMetadata).toString());
            if (data.nameListFilter != null) url = url.concat("&namefilter=" + data.nameListFilter);
            if (data.followedOnly != null) url = url.concat("&followedonly=" + new Boolean(data.followedOnly).toString());
            if (data.excludeBlocked != null) url = url.concat("&excludeblocked=" + new Boolean(data.excludeBlocked).toString());
            if (data.limit != null) url = url.concat("&limit=" + data.limit);
            if (data.offset != null) url = url.concat("&offset=" + data.offset);
            if (data.reverse != null) url = url.concat("&reverse=" + new Boolean(data.reverse).toString());
            return httpGetAsyncWithEvent(event, url);

        case "SEARCH_QDN_RESOURCES":
            url = "/arbitrary/resources/search?";
            if (data.service != null) url = url.concat("&service=" + data.service);
            if (data.query != null) url = url.concat("&query=" + data.query);
            if (data.identifier != null) url = url.concat("&identifier=" + data.identifier);
            if (data.name != null) url = url.concat("&name=" + data.name);
            if (data.names != null) data.names.forEach((x, i) => url = url.concat("&name=" + x));
            if (data.prefix != null) url = url.concat("&prefix=" + new Boolean(data.prefix).toString());
            if (data.exactMatchNames != null) url = url.concat("&exactmatchnames=" + new Boolean(data.exactMatchNames).toString());
            if (data.default != null) url = url.concat("&default=" + new Boolean(data.default).toString());
            if (data.includeStatus != null) url = url.concat("&includestatus=" + new Boolean(data.includeStatus).toString());
            if (data.includeMetadata != null) url = url.concat("&includemetadata=" + new Boolean(data.includeMetadata).toString());
            if (data.nameListFilter != null) url = url.concat("&namefilter=" + data.nameListFilter);
            if (data.followedOnly != null) url = url.concat("&followedonly=" + new Boolean(data.followedOnly).toString());
            if (data.excludeBlocked != null) url = url.concat("&excludeblocked=" + new Boolean(data.excludeBlocked).toString());
            if (data.limit != null) url = url.concat("&limit=" + data.limit);
            if (data.offset != null) url = url.concat("&offset=" + data.offset);
            if (data.reverse != null) url = url.concat("&reverse=" + new Boolean(data.reverse).toString());
            return httpGetAsyncWithEvent(event, url);

        case "FETCH_QDN_RESOURCE":
            url = "/arbitrary/" + data.service + "/" + data.name;
            if (data.identifier != null) url = url.concat("/" + data.identifier);
            url = url.concat("?");
            if (data.filepath != null) url = url.concat("&filepath=" + data.filepath);
            if (data.rebuild != null) url = url.concat("&rebuild=" + new Boolean(data.rebuild).toString())
            if (data.encoding != null) url = url.concat("&encoding=" + data.encoding);
            return httpGetAsyncWithEvent(event, url);

        case "GET_QDN_RESOURCE_STATUS":
            url = "/arbitrary/resource/status/" + data.service + "/" + data.name;
            if (data.identifier != null) url = url.concat("/" + data.identifier);
            return httpGetAsyncWithEvent(event, url);

        case "GET_QDN_RESOURCE_PROPERTIES":
            let identifier = (data.identifier != null) ? data.identifier : "default";
            url = "/arbitrary/resource/properties/" + data.service + "/" + data.name + "/" + identifier;
            return httpGetAsyncWithEvent(event, url);

        case "GET_QDN_RESOURCE_METADATA":
            identifier = (data.identifier != null) ? data.identifier : "default";
            url = "/arbitrary/metadata/" + data.service + "/" + data.name + "/" + identifier;
            return httpGetAsyncWithEvent(event, url);

        case "SEARCH_CHAT_MESSAGES":
            url = "/chat/messages?";
            if (data.before != null) url = url.concat("&before=" + data.before);
            if (data.after != null) url = url.concat("&after=" + data.after);
            if (data.txGroupId != null) url = url.concat("&txGroupId=" + data.txGroupId);
            if (data.involving != null) data.involving.forEach((x, i) => url = url.concat("&involving=" + x));
            if (data.reference != null) url = url.concat("&reference=" + data.reference);
            if (data.chatReference != null) url = url.concat("&chatreference=" + data.chatReference);
            if (data.hasChatReference != null) url = url.concat("&haschatreference=" + new Boolean(data.hasChatReference).toString());
            if (data.encoding != null) url = url.concat("&encoding=" + data.encoding);
            if (data.limit != null) url = url.concat("&limit=" + data.limit);
            if (data.offset != null) url = url.concat("&offset=" + data.offset);
            if (data.reverse != null) url = url.concat("&reverse=" + new Boolean(data.reverse).toString());
            return httpGetAsyncWithEvent(event, url);

        case "LIST_GROUPS":
            url = "/groups?";
            if (data.limit != null) url = url.concat("&limit=" + data.limit);
            if (data.offset != null) url = url.concat("&offset=" + data.offset);
            if (data.reverse != null) url = url.concat("&reverse=" + new Boolean(data.reverse).toString());
            return httpGetAsyncWithEvent(event, url);

        case "GET_BALANCE":
            url = "/addresses/balance/" + data.address;
            if (data.assetId != null) url = url.concat("&assetId=" + data.assetId);
            return httpGetAsyncWithEvent(event, url);

        case "GET_AT":
            url = "/at" + data.atAddress;
            return httpGetAsyncWithEvent(event, url);

        case "GET_AT_DATA":
            url = "/at/" + data.atAddress + "/data";
            return httpGetAsyncWithEvent(event, url);

        case "LIST_ATS":
            url = "/at/byfunction/" + data.codeHash58 + "?";
            if (data.isExecutable != null) url = url.concat("&isExecutable=" + data.isExecutable);
            if (data.limit != null) url = url.concat("&limit=" + data.limit);
            if (data.offset != null) url = url.concat("&offset=" + data.offset);
            if (data.reverse != null) url = url.concat("&reverse=" + new Boolean(data.reverse).toString());
            return httpGetAsyncWithEvent(event, url);

        case "FETCH_BLOCK":
            if (data.signature != null) {
                url = "/blocks/" + data.signature;
            } else if (data.height != null) {
                url = "/blocks/byheight/" + data.height;
            }
            url = url.concat("?");
            if (data.includeOnlineSignatures != null) url = url.concat("&includeOnlineSignatures=" + data.includeOnlineSignatures);
            return httpGetAsyncWithEvent(event, url);

        case "FETCH_BLOCK_RANGE":
            url = "/blocks/range/" + data.height + "?";
            if (data.count != null) url = url.concat("&count=" + data.count);
            if (data.reverse != null) url = url.concat("&reverse=" + data.reverse);
            if (data.includeOnlineSignatures != null) url = url.concat("&includeOnlineSignatures=" + data.includeOnlineSignatures);
            return httpGetAsyncWithEvent(event, url);

        case "SEARCH_TRANSACTIONS":
            url = "/transactions/search?";
            if (data.startBlock != null) url = url.concat("&startBlock=" + data.startBlock);
            if (data.blockLimit != null) url = url.concat("&blockLimit=" + data.blockLimit);
            if (data.txGroupId != null) url = url.concat("&txGroupId=" + data.txGroupId);
            if (data.txType != null) data.txType.forEach((x, i) => url = url.concat("&txType=" + x));
            if (data.address != null) url = url.concat("&address=" + data.address);
            if (data.confirmationStatus != null) url = url.concat("&confirmationStatus=" + data.confirmationStatus);
            if (data.limit != null) url = url.concat("&limit=" + data.limit);
            if (data.offset != null) url = url.concat("&offset=" + data.offset);
            if (data.reverse != null) url = url.concat("&reverse=" + new Boolean(data.reverse).toString());
            return httpGetAsyncWithEvent(event, url);

        case "GET_PRICE":
            url = "/crosschain/price/" + data.blockchain + "?";
            if (data.maxtrades != null) url = url.concat("&maxtrades=" + data.maxtrades);
            if (data.inverse != null) url = url.concat("&inverse=" + data.inverse);
            return httpGetAsyncWithEvent(event, url);

        default:
            // Pass to parent (UI), in case they can fulfil this request
            event.data.requestedHandler = "UI";
            parent.postMessage(event.data, '*', [event.ports[0]]);
            return;
    }

}, false);


/**
 * Listen for and intercept all link click events
 */
function interceptClickEvent(e) {
    var target = e.target || e.srcElement;
    if (target.tagName !== 'A') {
        target = target.closest('A');
    }
    if (target == null || target.getAttribute('href') == null) {
        return;
    }
    let href = target.getAttribute('href');
    if (href.startsWith("qortal://")) {
        const c = extractComponents(href);
        if (c != null) {
            qortalRequest({
                action: "LINK_TO_QDN_RESOURCE",
                service: c.service,
                name: c.name,
                identifier: c.identifier,
                path: c.path
            });
        }
        e.preventDefault();
    }
    else if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("//")) {
        // Block external links
        e.preventDefault();
    }
}
if (document.addEventListener) {
    document.addEventListener('click', interceptClickEvent);
}
else if (document.attachEvent) {
    document.attachEvent('onclick', interceptClickEvent);
}



/**
 * Intercept image loads from the DOM
 */
document.addEventListener('DOMContentLoaded', () => {
    const imgElements = document.querySelectorAll('img');
    imgElements.forEach((img) => {
        let url = img.src;
        const newUrl = convertToResourceUrl(url, false);
        if (newUrl != null) {
            document.querySelector('img').src = newUrl;
        }
    });
});

/**
 * Intercept img src updates
 */
document.addEventListener('DOMContentLoaded', () => {
    const imgElements = document.querySelectorAll('img');
    imgElements.forEach((img) => {
        let observer = new MutationObserver((changes) => {
            changes.forEach(change => {
                if (change.attributeName.includes('src')) {
                    const newUrl = convertToResourceUrl(img.src, false);
                    if (newUrl != null) {
                        document.querySelector('img').src = newUrl;
                    }
                }
            });
        });
        observer.observe(img, {attributes: true});
    });
});



const awaitTimeout = (timeout, reason) =>
    new Promise((resolve, reject) =>
        setTimeout(
            () => (reason === undefined ? resolve() : reject(reason)),
            timeout
        )
    );

function getDefaultTimeout(action) {
    if (action != null) {
        // Some actions need longer default timeouts, especially those that create transactions
        switch (action) {
            case "GET_USER_ACCOUNT":
                // User may take a long time to accept/deny the popup
                return 60 * 60 * 1000;

            case "FETCH_QDN_RESOURCE":
                // Fetching data can take a while, especially if the status hasn't been checked first
                return 60 * 1000;

            case "PUBLISH_QDN_RESOURCE":
                // Publishing could take a very long time on slow system, due to the proof-of-work computation
                // It's best not to timeout
                return 60 * 60 * 1000;

            case "SEND_CHAT_MESSAGE":
                // Chat messages rely on PoW computations, so allow extra time
                return 60 * 1000;

            case "JOIN_GROUP":
            case "DEPLOY_AT":
            case "SEND_COIN":
                // Allow extra time for other actions that create transactions, even if there is no PoW
                return 5 * 60 * 1000;

            default:
                break;
        }
    }
    return 10 * 1000;
}

/**
 * Make a Qortal (Q-Apps) request with no timeout
 */
const qortalRequestWithNoTimeout = (request) => new Promise((res, rej) => {
    const channel = new MessageChannel();

    channel.port1.onmessage = ({data}) => {
        channel.port1.close();

        if (data.error) {
            rej(data.error);
        } else {
            res(data.result);
        }
    };

    window.postMessage(request, '*', [channel.port2]);
});

/**
 * Make a Qortal (Q-Apps) request with the default timeout (10 seconds)
 */
const qortalRequest = (request) =>
    Promise.race([qortalRequestWithNoTimeout(request), awaitTimeout(getDefaultTimeout(request.action), "The request timed out")]);

/**
 * Make a Qortal (Q-Apps) request with a custom timeout, specified in milliseconds
 */
const qortalRequestWithTimeout = (request, timeout) =>
    Promise.race([qortalRequestWithNoTimeout(request), awaitTimeout(timeout, "The request timed out")]);


/**
 * Send current page details to UI
 */
document.addEventListener('DOMContentLoaded', () => {
    qortalRequest({
        action: "QDN_RESOURCE_DISPLAYED",
        service: _qdnService,
        name: _qdnName,
        identifier: _qdnIdentifier,
        path: _qdnPath
    });
});

/**
 * Handle app navigation
 */
navigation.addEventListener('navigate', (event) => {
    const url = new URL(event.destination.url);
    let fullpath = url.pathname + url.hash;
    qortalRequest({
        action: "QDN_RESOURCE_DISPLAYED",
        service: _qdnService,
        name: _qdnName,
        identifier: _qdnIdentifier,
        path: (fullpath.startsWith(_qdnBase)) ? fullpath.slice(_qdnBase.length) : fullpath
    });
});
