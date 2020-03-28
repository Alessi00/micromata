/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.plugins.core.impl;

import org.apache.commons.lang3.StringUtils;
import org.projectforge.continuousdb.SystemUpdater;
import org.projectforge.continuousdb.UpdateEntry;
import org.projectforge.framework.configuration.ConfigurationDao;
import org.projectforge.framework.configuration.ConfigurationParam;
import org.projectforge.framework.configuration.GlobalConfiguration;
import org.projectforge.framework.configuration.entities.ConfigurationDO;
import org.projectforge.framework.persistence.database.DatabaseService;
import org.projectforge.plugins.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Standard implementation of PluginAdminService.
 *
 * @author Roger Rene Kommer (r.kommer.extern@micromata.de)
 */
@Service
public class PluginAdminServiceImpl implements PluginAdminService
{
  private static final Logger LOG = LoggerFactory.getLogger(PluginAdminServiceImpl.class);

  @Autowired
  private ConfigurationDao configurationDao;

  @Autowired
  private ApplicationContext applicationContext;

  @Autowired
  private DatabaseService databaseService;

  @Autowired
  private List<PFPluginService> list;

  private List<PluginCallback> afterCreatedActivePluginsCallback = new ArrayList<>();

  @Override
  public List<AbstractPlugin> getActivePlugin()
  {
    PluginsRegistry pluginsRegistry = PluginsRegistry.instance();
    return pluginsRegistry.getPlugins();
  }

  @Override
  public List<AvailablePlugin> getAvailablePlugins()
  {

    Set<String> activated = getActivePlugins();
    ServiceLoader<PFPluginService> ls = ServiceLoader.load(PFPluginService.class);

    List<AvailablePlugin> ret = new ArrayList<>();
    for (PFPluginService e : ls) {
      AvailablePlugin ap = new AvailablePlugin(e, activated.contains(e.getPluginId()), e.isBuildIn());
      ret.add(ap);
    }
    return ret;
  }

  /**
   * read LocalSettings pf.plugins.active. If not defined, uses ConfigurationParam.
   *
   * @return the active plugins
   */
  public Set<String> getActivePlugins()
  {
    String activateds = GlobalConfiguration.getInstance().getStringValue(ConfigurationParam.PLUGIN_ACTIVATED);
    String[] sa = new String[0];
    if (!StringUtils.isBlank(activateds)) {
      sa = StringUtils.split(activateds, ", ");
    }
    Set<String> activated = new TreeSet<>(Arrays.asList(sa));
    return activated;
  }

  @Override
  public void initializeActivePlugins()
  {
    initializeActivePlugins(true);
  }

  @Override
  public void initializeAllPluginsForUnittest()
  {
    initializeActivePlugins(false);
  }

  protected void initializeActivePlugins(boolean onlyConfiguredActive)
  {
    List<AvailablePlugin> plugins = getAvailablePlugins();
    for (AvailablePlugin plugin : plugins) {
      LOG.info("Plugin found: " + plugin.getProjectForgePluginService().getPluginName());
      if (onlyConfiguredActive && !plugin.isActivated() && !plugin.isBuildIn()) {
        continue;
      }
      activatePlugin(plugin.getProjectForgePluginService());
    }

  }

  protected void activatePlugin(PFPluginService projectForgePluginService)
  {
    AbstractPlugin plugin = projectForgePluginService.createPluginInstance();
    AutowireCapableBeanFactory factory = applicationContext.getAutowireCapableBeanFactory();
    factory.initializeBean(plugin, projectForgePluginService.getPluginId());
    factory.autowireBean(plugin);
    PluginsRegistry.instance().register(plugin);
    plugin.init();
    setSystemUpdater(plugin);
    for (PluginCallback callback : afterCreatedActivePluginsCallback) {
      callback.call(plugin);
    }
    LOG.info("Plugin activated: " + projectForgePluginService.getPluginId());
  }

  @Override
  public boolean storePluginToBeActivated(String id, boolean activate)
  {
    Set<String> active = getActivePlugins();
    if (activate) {
      active.add(id);
    } else {
      active.remove(id);
    }
    String sval = StringUtils.join(active, ",");
    ConfigurationDO configuration = configurationDao.getEntry(ConfigurationParam.PLUGIN_ACTIVATED);
    if (configuration == null) {
      configuration = new ConfigurationDO();
      ConfigurationParam param = ConfigurationParam.PLUGIN_ACTIVATED;
      configuration.setParameter(param.getKey());
      configuration.setConfigurationType(param.getType());
      configuration.setGlobal(param.isGlobal());

    }

    configuration.setStringValue(sval);
    configurationDao.saveOrUpdate(configuration);
    GlobalConfiguration.getInstance().forceReload();
    return false;
  }

  @Override
  public void addExecuteAfterActivePluginCreated(PluginCallback run)
  {
    afterCreatedActivePluginsCallback.add(run);
  }

  private void setSystemUpdater(AbstractPlugin plugin)
  {
    SystemUpdater systemUpdater = databaseService.getSystemUpdater();
    final UpdateEntry updateEntry = plugin.getInitializationUpdateEntry();
    if (updateEntry != null) {
      if (!updateEntry.isInitial()) {
        LOG.error(
            "The given UpdateEntry returned by plugin.getInitializationUpdateEntry() is not initial! Please use constructor without parameter version: "
                + plugin.getClass());
      }
      systemUpdater.register(updateEntry);
    }
    final List<UpdateEntry> updateEntries = plugin.getUpdateEntries();
    if (updateEntries != null) {
      for (final UpdateEntry entry : updateEntries) {
        if (entry.isInitial()) {
          LOG.error(
              "The given UpdateEntry returned by plugin.getUpdateEntries() is initial! Please use constructor with parameter version: "
                  + plugin.getClass()
                  + ": "
                  + entry.getDescription());
        }
      }
      systemUpdater.register(updateEntries);
    }
  }
}
