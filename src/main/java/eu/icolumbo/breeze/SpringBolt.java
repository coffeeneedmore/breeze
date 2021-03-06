package eu.icolumbo.breeze;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;


/**
 * Spring for Storm bolts.
 * @author Pascal S. de Kloe
 */
public class SpringBolt extends SpringComponent implements IRichBolt {

	private static final Logger logger = LoggerFactory.getLogger(SpringBolt.class);

	private boolean doAnchor = true;
	private String[] passThroughFields = {};

	private OutputCollector collector;


	public SpringBolt(Class<?> beanType, String invocation, String... outputFields) {
		super(beanType, invocation, outputFields);
		logger.trace("{} constructed", this);
	}

	@Override
	public void prepare(Map stormConf, TopologyContext topologyContext, OutputCollector outputCollector) {
		collector = outputCollector;
		super.init(stormConf, topologyContext);
	}

	/**
	 * Registers the {@link #setPassThroughFields(String...) pass through}
	 * and the {@link #getOutputFields() output field names}.
	 */
	@Override
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		List<String> names = new ArrayList<>();
		for (String f : outputFields) names.add(f);
		for (String f : passThroughFields) names.add(f);
		declarer.declare(new Fields(names));
	}

	@Override
	public void execute(Tuple input) {
		logger.debug(format("%s got tuple '%s'", this, input.getMessageId()));

		try {
			Object[] arguments = new Object[inputFields.length];
			for (int i = arguments.length; --i >= 0;
				arguments[i] = input.getValueByField(inputFields[i]));

			for (Values output : invoke(arguments)) {
				for (String name : passThroughFields)
					output.add(input.getValueByField(name));

				if (doAnchor)
					collector.emit(input, output);
				else
					collector.emit(output);
			}

			collector.ack(input);
		} catch (InvocationTargetException e) {
			collector.reportError(e.getCause());
			collector.fail(input);
		}
	}

	@Override
	public void cleanup() {
	}

	/**
	 * Sets whether the tuple should be replayed in case of an error.
	 * @see <a href="https://github.com/nathanmarz/storm/wiki/Guaranteeing-message-processing">Storm Wiki</a>
	 */
	public void setDoAnchor(boolean value) {
		doAnchor = value;
	}

	/**
	 * Gets the field names which should be copied from the input tuple in addition to
	 * the {@link #getOutputFields() output fields}.
	 */
	public String[] getPassThroughFields() {
		return passThroughFields;
	}

	/**
	 * Gets the field names which should be copied from the input tuple in addition to
	 * the {@link #getOutputFields() output fields}.
	 */
	public void setPassThroughFields(String... value) {
		for (String name : value)
			for (String out : outputFields)
				if (name.equals(out))
					throw new IllegalArgumentException(name + "' already defined as output field");
		passThroughFields = value;
	}

}
