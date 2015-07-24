var instance;

var en_template = "<div></div>";
var selected_en;

var tabs;

var network;
var apps_short;
var apps = new Object();

function setDN(node, jspInstance)
{
    // Not using classic anchors as source, as we want the links to be perimeter links
    jspInstance.makeSource(node, {
        maxConnections: 100,
        endpoint: 'Blank',
        anchor: ["Perimeter", {shape: "Rectangle"}],
        filter: "div.arrow1",
        connectorStyle: {strokeStyle: 'red'}
    });

    jspInstance.makeSource(node, {
        maxConnections: 100,
        endpoint: 'Blank',
        anchor: ["Perimeter", {shape: "Rectangle"}],
        filter: "div.arrow2",
        connectorStyle: {strokeStyle: 'blue'}
    });

    jspInstance.makeTarget(node, {
        isTarget: true,
        maxConnections: 100,
        endpoint: 'Blank',
        allowLoopback: false,
        anchor: ["Perimeter", {shape: "Rectangle"}]
    });

    jspInstance.draggable(node, {containment: "parent", filter: 'div.anchor', filterExclude: true});
}

function initNetwork()
{
    var network_panel_root = "node-c";

    var jspInstance = jsPlumb.getInstance({
        Connector: ["Bezier", {curviness: 50}],
        DragOptions: {cursor: "pointer", zIndex: 2000},
        PaintStyle: {strokeStyle: "gray", lineWidth: 2},
        EndpointStyle: {radius: 9, fillStyle: "gray"},
        ConnectionOverlays: [['Arrow', {width: 12, location: 1}]],
        HoverPaintStyle: {strokeStyle: "#ec9f2e"},
        EndpointHoverStyle: {fillStyle: "#ec9f2e"},
        Container: network_panel_root
    });

    $('.dn').each(function ()
    {
        setDN(this, jspInstance);
    });

    $('#' + network_panel_root).droppable({drop: function (event, ui) {
            var t = ui.helper.clone();
            t.appendTo(this);
            setDN(t, instance);
        }});

    $("#" + network_panel_root).on('click', '.dn', function ()
    {
        var node = $(this);
        selected_en = this;
        $('.dn').removeClass('drawingPanel-selected');
        node.addClass('drawingPanel-selected');
        $("#pn-name").val(node[0]._source.name);
        $("#pn-qport").val(node[0]._source.qPort);
        $("#pn-httpport").val(node[0]._source.wsPort);
        $("#pn-engine").prop('checked', !node[0]._source.simpleRunner);
    });

    $("#phynodes-details input").change(function ()
    {
        var n = selected_en._source;
        n.name = $("#pn-name").val();
        n.qPort = $("#pn-qport").val();
        n.wsPort = $("#pn-httpport").val();
        n.simpleRunner = !$("#pn-engine").prop('checked');
        $(selected_en).html(getExecNodeContent(n));
    });

    $.getJSON("ws/meta/network").done(function (data)
    {
        network = data;

        // First pass: draw nodes
        $.each(data.nodes, function ()
        {
            var d = getExecNodeDiv(this);
            d[0]._source = this;
            d.appendTo($("#node-c"));
            d.css('left', this.x);
            d.css('top', this.y);
            setDN(d, jspInstance);
        });

        // Second pass: links
        $.each(data.nodes, function ()
        {
            var source = this;
            $.each(this.toTCP, function ()
            {
                jspInstance.connect({source: source.id, target: this.toString()});
            });
        });

        // Done - init other panels
        initPlaces();

    }).fail(function (o, status)
    {
        alert("failed to fetch network " + status);
    });
}

function getExecNodeDiv(node)
{
    var d = $("<div id='" + node.id + "' class='dn execnode'>" + getExecNodeContent(node) + "</div>");
    if (node.console)
    {
        d.addClass('execnode-console');
    }
    return d;
}

function getExecNodeContent(node)
{
    return "<div>" + node.name + "</div><div class='dn-smalltext'>" + node.dns + ":" + node.qPort +
            "<div class='anchor arrow1' style='position: absolute; bottom: -15px; left: 10%;'>remote control</div>" +
            "<div class='anchor arrow2' style='position: absolute; bottom: -15px; left: 70%;'>channel</div>" +
            "</div>";
}

function initIdIfNone(changes, action)
{
    if (!changes)
    {
        return;
    }
    var grid = this;
    $.each(changes, function ()
    {
        var n = grid.getSourceDataAtRow(this[0]);
        if (!n.id)
        {
            n.id = uuid.v4();
        }
    });
}

$(
        function ()
        {
            tabs = $('#tabs').tabs({
                active: 0,
                heightStyle: 'fill'
            });

            initAppChoice();
            initNetwork();
        }
);

Number.prototype.zeroPad = function (numZeros)
{
    var n = Math.abs(this);
    var zeros = Math.max(0, numZeros - Math.floor(n).toString().length);
    var zeroString = Math.pow(10, zeros).toString().substr(1);
    if (this < 0) {
        zeroString = '-' + zeroString;
    }
    return zeroString + n;
};

function getTomorrowPlus(days)
{
    if (!days)
    {
        days = 0;
    }
    var d = new Date();
    d.setHours(0);
    d.setMinutes(0);
    d.setSeconds(0);
    d.setMilliseconds(0);
    d.setDate(d.getDate() + 1 + days);
    return d;
}

$.postJSON = function (url, data, callback) {
    return jQuery.ajax({
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        'type': 'POST',
        'url': url,
        'data': JSON.stringify(data),
        'dataType': 'json',
        'success': callback
    });
};