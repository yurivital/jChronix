function nameDescriptionFilter(item, args)
{
	if (args.searchString != "" && (item["_name"].indexOf(args.searchString) == -1 && item["_description"].indexOf(args.searchString) == -1))
	{
		return false;
	}
	return true;
}

function comparer(a, b)
{
	var x = a[sortcol], y = b[sortcol];
	return x.toLowerCase().localeCompare(y.toLowerCase());
}

function requiredFieldValidator(value)
{
	if (value == null || value == undefined || !value.length)
	{
		var res =
		{
			valid : false,
			msg : "This is a required field"
		};
		return res;
	}
	else
	{
		var res =
		{
			valid : true,
			msg : null
		};
		return res;
	}
}

function delBtFormatter(row, cell, value, columnDef, dataContext)
{
	return "<button class='del" + dataContext.constructor.name + "'  type='button' id='" + dataContext.id + "' >X</button>";
}

function dnsFormatter(row, cell, value, columnDef, dataContext)
{
	for ( var i = 0; i < columnDef.options.length; i++)
	{
		var node = columnDef.options[i];
		if (node._id === value)
			return node.prettyLabel();
	}
	return value;
}

function onSort(e, args)
{
	sortdir = args.sortAsc ? 1 : -1;
	sortcol = args.sortCol.field;

	this.dataview.sort(comparer, args.sortAsc);
};

function onCellChange(e, args)
{
	this.dataview.updateItem(args.item._id, args.item);
}

function onRowCountChanged(e, args)
{
	this.mainGrid.updateRowCount();
	this.mainGrid.render();
}

function onRowsChanged(e, args)
{
	this.mainGrid.invalidateRows(args.rows);
	this.mainGrid.render();
}

function searchBoxKeyup(e)
{
	Slick.GlobalEditorLock.cancelCurrentEdit();

	// Clear on Esc
	if (e.which == 27)
	{
		$(e.currentTarget).val("");
	}
	// Update filter
	this.dataview.setFilterArgs(
	{
		searchString : $.trim($(e.currentTarget).val()),
	});

	// Refresh dataview
	this.dataview.refresh();
}

function slResize()
{
	if (!this.slickContainer.is(':visible'))
		return;
	this.slickContainer.height(0);
	this.slickContainer.height(this.containerContainer.height());
	this.slickContainer.width(this.containerContainer.width());
	this.mainGrid.resizeCanvas();
};

var slickGridOptionsEditable =
{
	editable : true,
	enableAddRow : true,
	enableCellNavigation : true,
	enableColumnReorder : false,
	enableRowReordering : false,
	asyncEditorLoading : true,
	showHeaderRow : false,
	multiSelect : false,
	autoEdit : true,
	enableTextSelectionOnCells : false,
	autoHeight : false,
	forceFitColumns : true,
	fullWidthRows : true,
	explicitInitialization : true,
	syncColumnCellResize : true,
};

function getSlickGridOptionsEditable()
{
	return $.extend(
	{}, slickGridOptionsEditable);
}

var slickGridOptionsReadOnly =
{
	editable : false,
	enableAddRow : false,
	enableCellNavigation : true,
	enableColumnReorder : false,
	enableRowReordering : false,
	asyncEditorLoading : false,
	showHeaderRow : false,
	multiSelect : false,
	autoEdit : false,
	enableTextSelectionOnCells : false,
	autoHeight : false,
	forceFitColumns : true,
	fullWidthRows : true,
	explicitInitialization : true,
	syncColumnCellResize : true,
};

function getSlickGridOptionsReadOnly()
{
	return $.extend(
	{}, slickGridOptionsReadOnly);
}

/*
 * Simple dropdown editor for SlickGrid options should be an array of items with an item and a name.
 */
function SelectCellEditor(args)
{
	var $select = $("<SELECT tabIndex='0' class='editor-select'></SELECT>"); // Value is just to avoid warnings
	var defaultValue = 0;

	this.init = function()
	{
		var items = args.column.options;

		option_str = "";
		for ( var i = 0; i < items.length; i++)
		{
			var v = items[i];
			option_str += "<OPTION value='" + v._id + "'>" + v.prettyLabel() + "</OPTION>";
		}
		$select = $("<SELECT tabIndex='0' class='editor-select'>" + option_str + "</SELECT>");
		$select.appendTo(args.container);
		$select.focus();
	};

	this.destroy = function()
	{
		$select.remove();
	};

	this.focus = function()
	{
		$select.focus();
	};

	this.loadValue = function(item)
	{
		defaultValue = item[args.column.field];
		$select.val(defaultValue);
	};

	this.serializeValue = function()
	{
		return $select.val();
	};

	this.applyValue = function(item, state)
	{
		item[args.column.field] = state;
	};

	this.isValueChanged = function()
	{
		return ($select.val() != defaultValue);
	};

	this.validate = function()
	{
		var res =
		{
			valid : true,
			msg : null
		};
		return res;
	};

	this.init();
}