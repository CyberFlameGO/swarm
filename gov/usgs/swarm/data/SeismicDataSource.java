package gov.usgs.swarm.data;

import gov.usgs.plot.data.HelicorderData;
import gov.usgs.plot.data.RSAMData;
import gov.usgs.plot.data.Wave;

import java.util.List;

import javax.swing.event.EventListenerList;

/**
 * Base class for seismic data sources.
 * 
 * @author Dan Cervelli
 */
abstract public class SeismicDataSource {
	protected String name = "Unnamed Data Source";
	protected boolean storeInUserConfig = true;
	protected boolean useCache = true;
	protected int minimumRefreshInterval = 1;

	protected EventListenerList listeners = new EventListenerList();

	public Gulper createGulper(GulperList gl, String k, String ch, double t1, double t2, int size, int delay) {
		return new Gulper(gl, k, this, ch, t1, t2, size, delay);
	}

	abstract public List<String> getChannels();

	abstract public void parse(String params);

	/**
	 * Either returns the wave successfully or null if the data source could
	 * not get the wave.
	 * 
	 * @param station
	 * @param t1
	 * @param t2
	 * @return wave if possible
	 */
	abstract public Wave getWave(String station, double t1, double t2);

	abstract public HelicorderData getHelicorder(String station, double t1, double t2, GulperListener gl);

	abstract public String toConfigString();

	protected SeismicDataSource() {
		// explicit default constructor needed for reflection
	}

	public void addListener(SeismicDataSourceListener l) {
		listeners.add(SeismicDataSourceListener.class, l);
	}

	public void removeListener(SeismicDataSourceListener l) {
		listeners.remove(SeismicDataSourceListener.class, l);
	}

	public void fireChannelsUpdated() {
		Object[] ls = listeners.getListenerList();
		for (int i = ls.length - 2; i >= 0; i -= 2)
			if (ls[i] == SeismicDataSourceListener.class)
				((SeismicDataSourceListener) ls[i + 1]).channelsUpdated();
	}

	public void fireChannelsProgress(String id, double p) {
		Object[] ls = listeners.getListenerList();
		for (int i = ls.length - 2; i >= 0; i -= 2)
			if (ls[i] == SeismicDataSourceListener.class)
				((SeismicDataSourceListener) ls[i + 1]).channelsProgress(id, p);
	}

	public void fireHelicorderProgress(String id, double p) {
		Object[] ls = listeners.getListenerList();
		for (int i = ls.length - 2; i >= 0; i -= 2)
			if (ls[i] == SeismicDataSourceListener.class)
				((SeismicDataSourceListener) ls[i + 1]).helicorderProgress(id, p);
	}

	public void notifyDataNotNeeded(String station, double t1, double t2, GulperListener gl) {
	}

	public void setStoreInUserConfig(boolean b) {
		storeInUserConfig = b;
	}

	public boolean isStoreInUserConfig() {
		return storeInUserConfig;
	}

	public void setUseCache(boolean b) {
		useCache = b;
	}

	public boolean isUseCache() {
		return useCache;
	}

	/**
	 * Is this data source active; that is, is new data being added in real-time
	 * to this data source?
	 * 
	 * @return whether or not this is an active data source
	 */
	public boolean isActiveSource() {
		return false;
	}

	/**
	 * Close the data source.
	 */
	public void close() {
	}

	public void remove() {
	}

	/**
	 * Get a copy of the data source. The default implementation returns an
	 * identical copy, that is, <code>this</code>.
	 * 
	 * This is confusing. Why return a reference to an object the caller already has? Should this really return a
	 * clone? If so, why not call it clone()? --tjp
	 * 
	 * @return the identical data source (this)
	 */
	public SeismicDataSource getCopy() {
		return this;
	}

	/**
	 * Get a string representation of this data source. The default implementation
	 * return the name of the data source.
	 * 
	 * @return the string representation of this data source
	 */
	public String toString() {
		return name;
	}

	/**
	 * Sets the data source name.
	 * 
	 * @param s
	 *            the new name
	 */
	public void setName(String s) {
		name = s;
	}

	/**
	 * Gets the data source name.
	 * 
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	public void establish() {
	}

	public int getMinimumRefreshInterval() {
		return minimumRefreshInterval;
	}

}
