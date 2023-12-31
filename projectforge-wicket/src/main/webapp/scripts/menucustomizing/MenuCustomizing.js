(function () {

  //to simulate drag event
  var is_dragging = false;

  //to indicate if entry is editing
  var is_editing = false;

  //to indicate if configuration is active
  var is_configuration_active = false;

  $(window).load(function () {
    //handle new entries
    $('.pf_safenewentry').click(function () {
      addfolder();
    });

    var $pfNewentry = $('#pf_newentry input');

    $pfNewentry.keyup(function (event) {
      if (event.which === 13) {
        addfolder();
      }
    });

    $pfNewentry.keyup(function (event) {
      if (event.which === 27) {
        //esc
      }
    });

    $pfNewentry.blur(function () {
      $(this).parent().removeClass('pf_warning');
    });

    //open & close configuration on click
    $('#mm_configure').click(function () {
      if (is_configuration_active === false) {
        openConfiguration();
      } else if (is_configuration_active === true) {
        closeConfiguration();
      }
    });

    //handle click outside configuration panel to close it
    $('html').click(function () {
      closeConfiguration();
    });
    $('.pf_menueconfig, #pf_sortablecontainer').click(function (event) {
      event.stopPropagation();
    });

    //initially do stuff
    updatelayout();
    mousemoveactions();
    setdraggable();
    fixresponsive();
    addMouseOverOnDrag();
  });


  //save the menu configuration
  function update() {
    // var callbackUrl = $("#mm_configure").data("callback");
    var callbackUrl = "/rs/menucustomization/customize";
    $.post(callbackUrl, calcConfiguration());

    $('.pf_menueconfig .dropdown-menu').css('top', $('.pf_sortable').height());
    $('#pf_sortablecontainer').css('height', $('.pf_sortable').height());
  }

  function updatelayout() {
    $('#pf_sortablecontainer').width($('#pf_sortablecontainer ul.nav').width());
    $('.pf_menueconfig .dropdown-menu').css('top', $('.pf_sortable').height());
    $('#pf_sortablecontainer').css('height', $('.pf_sortable').height());
    $('#pf_newentry input').parent().removeClass('pf_warning');
  }

  //mousemove actions
  function mousemoveactions() {
    $('body').mousemove(function (e) {
      if (is_dragging) {
        //position draghelper on mousemove
        $('.pf_draghelper').css({'top': e.pageY + 20, 'left': e.pageX + 20});
        updatelayout();
      }
    });
  }

  //add mouseover event on drag
  function addMouseOverOnDrag() {
    $.each($('#pf_sortablecontainer .dropdown, #pf_sortablecontainer .dropdown-submenu'), function () {
      $(this).mouseenter(function () {
        if ($(window).width() > 879) {
          $(this).addClass('open');
        }
      });
      $(this).mouseleave(function () {
        $(this).removeClass('open');
      });
    });
  }

  //add a new dropdownmenu folder
  function addfolder() {
    if ($('#pf_newentry input').val().length == 0) {
      $('#pf_newentry').addClass('pf_warning');
    } else {
      $('#pf_newentry').after('<li class="dropdown"><a href="#" class="dropdown-toggle" data-toggle="dropdown">' + $('#pf_newentry input').val() + '<b class="caret"></b></a><ul class="dropdown-menu"></ul></li>');
      $('#pf_newentry input').val('');
      makesortable();
      update();
      addMouseOverOnDrag();
      clickhandling();
      $('#pf_sortablecontainer').width($('#pf_sortablecontainer ul.nav').width());
    }
  }

  //to start the configuration
  function openConfiguration() {
    is_configuration_active = true;
    //highlight target container
    $('#pf_newentry').css('display', 'block');
    $('#pf_sortablecontainer ul.nav').addClass('pf_sortable');
    $('#pf_sortablecontainer, .pf_menueconfig').addClass('pf_configuration_active');
    clickhandling();
    updatelayout();
    makesortable();
  }

  //to stop the configuration
  function clickhandling() {
    $.each($('.pf_menueconfig ul a'), function () {
      $(this).on('click.dragableScope', function () {
        return false;
      });
    });
    $.each($('#pf_sortablecontainer li a:not(.pf_safenewentry, .pf_delete, .pf_save)'), function () {
      $(this).on('click.selectableScope', function () {
        if (!is_editing) {
          $('#pf_sortablecontainer ul').sortable('disable');
          is_editing = true;
          $(this).after('<div id="pf_editentry" class="pf_disable"><input type="text" class="text"><a class="pf_save"><i class="glyphicon glyphicon-ok"></i></a><a class="pf_delete"><i class="glyphicon glyphicon-trash"></i></a></div>');
          if ($(this).parent().find('ul').children().length == 0) {
            $('.pf_delete').css('display', 'block');
          } else {
            $('.pf_delete').css('display', 'none');
          }
          $(this).css('display', 'none');
          updatelayout();
          innerItemsVal = $(this).html().split('<span');
          $('#pf_editentry input').val(getTopText($(this)));
          $('#pf_editentry input').focus();
          $('#pf_editentry input').keyup(function (event) {
            if (event.keyCode == 13) {
              $('#pf_editentry .pf_save').mousedown();
            }
          });
          $('#pf_editentry .pf_save').mousedown(function () {
            if ($('#pf_editentry input').val().length == 0) {
              $('#pf_editentry').addClass('pf_warning');
            } else {
              var value = escapeHtml($('#pf_editentry input').val());
              if ($(this).parent().prev().find('b').length) {
                $(this).parent().prev().html(value + '<b class="caret"></b>');
              } else if ($(this).parent().prev().find('span').length) {
                innerItems = $(this).parent().prev().html().split('<span');
                $(this).parent().prev().html(value + '<span' + innerItems[1]);
              } else {
                $(this).parent().prev().text(value);
              }
              $(this).parent().prev().css('display', 'block');
              removeeditentry();
              updatelayout();
              update();
              $('#pf_sortablecontainer ul').sortable('enable');
            }
          });
          $('#pf_editentry .pf_delete').mousedown(function () {
            $(this).parent().parent().remove();
            removeeditentry();
            updatelayout();
            $('#pf_sortablecontainer ul').sortable('enable');
            update();
          });
          $('#pf_editentry input').blur(function () {
            updatelayout();
            $('#pf_sortablecontainer ul').sortable('enable');
            $(this).parent().prev().css('display', 'block');
            removeeditentry();
          });
        }
        return false;
      });
    });
  }

  //remove editentry
  function removeeditentry() {
    $('#pf_editentry').remove();
    is_editing = false;
  }

  //to stop the configuration
  function closeConfiguration() {
    if (is_configuration_active) {
      destroysortable();
      $('#pf_newentry').css('display', 'none');
      $('#selector').off('.myScope');
      $.each($('#pf_sortablecontainer a, .pf_menueconfig ul a'), function () {
        $(this).off('.selectableScope, dragableScope');
      });
    }
    is_configuration_active = false;
    $('#pf_sortablecontainer ul.nav').removeClass('pf_sortable');
    $('#pf_newentry input').val('');
    $('#pf_sortablecontainer, .pf_menueconfig').removeClass('pf_configuration_active');
    $('.pf_menueconfig > li').removeClass('open');
    if ($(window).width() < 879) {
      $('#pf_sortablecontainer > ul').css('left', '0');
    } else {
      $('#pf_sortablecontainer > ul').css('left', '70');
    }
    updatelayout();
    removeeditentry();
  }

  //fix responsive
  function fixresponsive() {
    $(window).resize(function () {
      if ($(window).width() < 879) {
        closeConfiguration();
        $('#pf_sortablecontainer > ul').css('left', '0px');
        $('#pf_sortablecontainer').css('width', '');
        $('#pf_sortablecontainer').css('height', '');
      }
      if ($(window).width() > 879 && !is_configuration_active) {
        $('#pf_sortablecontainer > ul').css('left', '70px');
        $('#pf_sortablecontainer').css('height', $('.pf_sortable').height());
        updatelayout();
      }
    });
  }

  //set draggable
  function setdraggable() {
    $.each($('#draggable ul li'), function () {
      $(this).draggable({
        stack: $(this),
        cursor: 'pointer',
        revert: false,
        helper: 'clone',
        zIndex: 20000,
        opacity: 0.01,
        connectToSortable: '#pf_sortablecontainer ul',
        drag: function (event, ui) {
          //change width of target container dynamically
          updatelayout();
          //position draghelper on mousemove
          $('body').mousemove(function (e) {
            $('.pf_draghelper').css({'top': e.pageY + 20, 'left': e.pageX + 20})
          });
        },
        start: function (event, ui) {
          //attach draghelper to dom (z-index problem)
          $('body').after('<div class="pf_draghelper">' + ui.helper.prevObject.context.innerText + '</div>');
        },
        stop: function (event, ui) {
          //close all dropdowns
          $.each($('#pf_sortablecontainer .dropdown'), function () {
            $(this).removeClass('open');
          });
          //remove draghelper
          $('.pf_draghelper').remove();
        }
      });
    });
  }

  //destroy sortable to all lists
  function destroysortable() {
    $('#pf_sortablecontainer ul').sortable('destroy');
  }

  //set sortable to all lists
  function makesortable() {
    $('#pf_sortablecontainer ul').sortable({
      connectWith: $('#pf_sortablecontainer ul'),
      tolerance: 'pointer',
      items: '> li:not(.pf_disable li, .pf_disable, :empty)',
      cursor: 'pointer',
      delay: 200,
      cursorAt: {top: -20},
      zIndex: 20000,
      placeholder: 'pf_sortable-placeholder',
      receive: function (e, ui) {
        sortableIn = 1;
        $(ui.draggable).appendTo(this);
        if (ui.item.hasClass('dropdown') || ui.item.hasClass('dropdown-submenu')) {
          if (ui.item.parent().hasClass('dropdown-menu')) {
            ui.item.removeClass('dropdown');
            ui.item.addClass('dropdown-submenu');
          } else if (ui.item.parent().hasClass('nav')) {
            ui.item.removeClass('dropdown-submenu');
            ui.item.addClass('dropdown');
          }
        }
      },
      over: function (e, ui) {
        sortableIn = 1;
      },
      out: function (e, ui) {
        sortableIn = 0;
      },
      start: function (event, ui) {
        is_dragging = true;
        //attach draghelper to dom (z-index)
        innerItemsArr = ui.item.context.innerText.split("\n");
        $('body').after('<div class="pf_draghelper">' + innerItemsArr[0] + '</div>');
      },
      stop: function (event, ui) {
        is_dragging = false;
        updatelayout();
        //remove draghelper
        $('.pf_draghelper').remove();
        clickhandling();
      },
      update: function () {
        update();
      }
    }).disableSelection();
  }

  function calcConfiguration() {
    var xmlDocument = $.parseXML("<root/>");
    $("#pf_sortablecontainer > ul > li").each(function() {
      if($(this).attr("id") !== "pf_newentry") {
        putXml(xmlDocument, xmlDocument.documentElement, $(this));
      }
    });
    var serialized;
    try {
      // XMLSerializer exists in current Mozilla browsers
      serializer = new XMLSerializer();
      serialized = serializer.serializeToString(xmlDocument);
    }
    catch (e) {
      // Internet Explorer has a different approach to serializing XML
      serialized = xmlDocument.xml;
    }
    return {configuration: serialized };
  }

  function putXml(xmlDocument, xmlParent, node) {
    var element = $(node).children("a");
    var ref = element.attr('ref');
    // Get content text without text of children (such as span's etc.):
    var content = getTopText(element);
    var xmlNode = xmlDocument.createElement('item');
    xmlNode.appendChild(document.createTextNode(content));
    xmlParent.appendChild(xmlNode);
    if(node.children("ul").length == 0) {
      xmlNode.setAttribute("id", ref);
    } else {
      // node
      $(node).children("ul").children("li").each(function() {
        putXml(xmlDocument, xmlNode, $(this));
      });
    }
  }

  // Gets the first text without text of descendants.
  function getTopText(element) {
    return element.clone().children().remove().end().text().trim();
  }

  //List of HTML entities for escaping.
  var htmlEscapes = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#x27;',
    '/': '&#x2F;'
  };

  // Regex containing the keys listed immediately above.
  var htmlEscaper = /[&<>"'\/]/g;

  //Escape a string for HTML interpolation.
  function escapeHtml(string) {
    return ('' + string).replace(htmlEscaper, function(match) {
      return htmlEscapes[match];
    });
  }

})();
