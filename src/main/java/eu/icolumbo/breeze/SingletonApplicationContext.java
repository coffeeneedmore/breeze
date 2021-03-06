package eu.icolumbo.breeze;

import backtype.storm.Config;
import backtype.storm.task.TopologyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasText;


/**
 * Spring registry.
 * @author Pascal S. de Kloe
 * @author Jethro Bakker
 */
public enum SingletonApplicationContext {

	INSTANCE;

	private static final Logger logger = LoggerFactory.getLogger(SingletonApplicationContext.class);

	private final Map<String,ApplicationContext> registry = new HashMap<>();


	/**
	 * Gets the Spring setup for the respective Storm topology.
	 */
	public static synchronized ApplicationContext get(Map stormConf, TopologyContext topologyContext) {
		String topologyName = (String) stormConf.get(Config.TOPOLOGY_NAME);
		if (! hasText(topologyName))
			throw new IllegalStateException("Missing '" + Config.TOPOLOGY_NAME + "' in Storm configuration");

		logger.debug("Application context lookup for topology '{}'", topologyName);

		Map<String,Object> properties = Collections.checkedMap(stormConf, String.class, Object.class);

		ApplicationContext entry = INSTANCE.registry.get(topologyName);
		if (entry == null) {
			logger.debug("Need new application context");
			entry = instantiate(properties);
			logger.info("Application context instantiated for topology '{}'", topologyName);
			INSTANCE.registry.put(topologyName, entry);
		}

		return entry;
	}

	private static ApplicationContext instantiate(Map<String, Object> stormConf) {
		String topologyName = (String) stormConf.get(Config.TOPOLOGY_NAME);
		String[] configLocations = {format("classpath:/%s-context.xml", topologyName)};
		AbstractApplicationContext result = new ClassPathXmlApplicationContext(configLocations, false);
		result.setId(configLocations[0]);

		if (!stormConf.isEmpty()) {
			logger.debug("Applying Storm configuration: {}", stormConf);
			PropertySource propertySource = new MapPropertySource("storm-configuration", stormConf);
			result.getEnvironment().getPropertySources().addLast(propertySource);
		}

		result.refresh();
		return result;
	}

}
