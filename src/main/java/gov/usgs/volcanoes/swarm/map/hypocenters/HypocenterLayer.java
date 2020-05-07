package gov.usgs.volcanoes.swarm.map.hypocenters;

import gov.usgs.volcanoes.core.legacy.plot.render.DataPointRenderer;
import gov.usgs.volcanoes.core.math.proj.GeoRange;
import gov.usgs.volcanoes.core.math.proj.Projection;
import gov.usgs.volcanoes.core.time.J2kSec;
import gov.usgs.volcanoes.core.time.Time;
import gov.usgs.volcanoes.quakeml.Event;
import gov.usgs.volcanoes.quakeml.EventSet;
import gov.usgs.volcanoes.quakeml.Magnitude;
import gov.usgs.volcanoes.quakeml.Origin;
import gov.usgs.volcanoes.quakeml.QuakemlObserver;
import gov.usgs.volcanoes.quakeml.QuakemlSource;
import gov.usgs.volcanoes.swarm.ConfigListener;
import gov.usgs.volcanoes.swarm.Swarm;
import gov.usgs.volcanoes.swarm.SwarmConfig;
import gov.usgs.volcanoes.swarm.map.MapFrame;
import gov.usgs.volcanoes.swarm.map.MapLayer;
import gov.usgs.volcanoes.swarm.map.MapPanel;
import gov.usgs.volcanoes.swarm.map.MapPanel.ColorSetting;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HypocenterLayer implements MapLayer, ConfigListener, QuakemlObserver {
  private static final Logger LOGGER = LoggerFactory.getLogger(HypocenterLayer.class);
  private static final int REFRESH_INTERVAL = 5 * 60 * 1000;
  private static final int ONE_HOUR = 60 * 60 * 1000;
  private static final int ONE_DAY = ONE_HOUR * 24;
  private static final int ONE_WEEK = ONE_DAY * 7;

  private static final int POPUP_PADDING = 2;

  private static final int[] markerSize = { 5, 7, 9, 11, 13, 17, 21, 25, 29, 33, 37 };

  private static final Color ORANGE = new Color(225, 175, 0, 200);
  private static final Color RED = new Color(200, 0, 0, 200);
  private static final Color YELLOW = new Color(225, 225, 0, 200);
  private static final Color WHITE = new Color(200, 200, 200, 200);
  private static final Color GREEN = new Color(0, 200, 0, 200);
  private static final Color BLUE = new Color(0, 0, 200, 200);
  private static final Color PURPLE = new Color(200, 0, 200, 200);
  private static final Color BLACK = new Color(0, 0, 0, 200);

  private final Map<String, Event> events;
  private final Map<String, Event> importedEvents;

  private MapPanel panel;

  private final SwarmConfig swarmConfig;
  private final DataPointRenderer renderer;
  private QuakemlSource quakemlSource;
  private Event hoverEvent;

  /**
   * Constructor.
   * 
   * @throws MalformedURLException exception
   */
  public HypocenterLayer() throws MalformedURLException {
    events = new ConcurrentHashMap<String, Event>();
    importedEvents = new ConcurrentHashMap<String, Event>();
    swarmConfig = SwarmConfig.getInstance();
    swarmConfig.addListener(this);

    renderer = new DataPointRenderer();
    renderer.antiAlias = true;
    renderer.stroke = new BasicStroke(1f);
    renderer.filled = true;
    renderer.color = Color.BLACK;

    HypocenterSource hypocenterSource = swarmConfig.getHypocenterSource();

    if (hypocenterSource != HypocenterSource.NONE) {
      URL quakemlUrl = new URL(hypocenterSource.getUrl());
      if (quakemlUrl != null) {
        quakemlSource = new QuakemlSource(quakemlUrl, (long) REFRESH_INTERVAL);
        quakemlSource.addObserver(this);
        update(quakemlSource);
      }
    }
  }

  public void setMapPanel(MapPanel mapPanel) {
    panel = mapPanel;
  }

  /**
   * Draw.
   * 
   * @see gov.usgs.volcanoes.swarm.map.MapLayer#draw(java.awt.Graphics2D)
   */
  public void draw(Graphics2D g2) {
    if (events.size() < 1) {
      return;
    }

    GeoRange range = panel.getRange();
    Projection projection = panel.getProjection();
    int widthPx = panel.getGraphWidth();
    int heightPx = panel.getGraphHeight();
    int insetPx = panel.getInset();

    for (final Event event : events.values()) {
      Origin origin = event.getPreferredOrigin();
      if (origin == null) {
        continue;
      }
      Point2D.Double originLoc = new Point2D.Double(origin.getLongitude(), origin.getLatitude());
      if (!range.contains(originLoc)) {
        continue;
      }

      final Point2D.Double xy = projection.forward(originLoc);

      final double[] ext = range.getProjectedExtents(projection);
      final double dx = (ext[1] - ext[0]);
      final double dy = (ext[3] - ext[2]);
      final Point2D.Double res = new Point2D.Double();
      res.x = (((xy.x - ext[0]) / dx) * widthPx + insetPx);
      res.y = ((1 - (xy.y - ext[2]) / dy) * heightPx + insetPx);

      g2.translate(res.x, res.y);

      Magnitude magnitude = event.getPreferredMagnitude();
      float diameter = 5;
      if (magnitude != null) {
        diameter = getMarkerSize(magnitude.getMagnitude().getValue());
      }
      renderer.shape = new Ellipse2D.Float(0f, 0f, diameter, diameter);


      Color markerColor;
      if (panel.getColorSetting() == ColorSetting.DEPTH) {
        double depth = origin.getDepth() / 1000; // km
        if (event == hoverEvent) {
          markerColor = GREEN;
        } else if (Double.isNaN(depth)) {
          markerColor = WHITE;
        } else if (depth < 0) {
          markerColor = RED;
        } else if (depth < 5) {
          markerColor = ORANGE;
        } else if (depth < 13) {
          markerColor = YELLOW;
        } else if (depth < 20) {
          markerColor = GREEN;
        } else if (depth < 40) {
          markerColor = BLUE;
        } else {
          markerColor = PURPLE;
        }
      } else { // color events by age
        long age = J2kSec.asEpoch(J2kSec.now()) - origin.getTime();
        if (event == hoverEvent) {
          markerColor = GREEN;
        } else if (age < ONE_HOUR) {
          markerColor = RED;
        } else if (age < ONE_DAY) {
          markerColor = ORANGE;
        } else if (age < ONE_WEEK) {
          markerColor = YELLOW;
        } else {
          markerColor = WHITE;
        }
      }

      // int alpha = 0x80FFFFFF;
      // renderer.paint = new Color(alpha & markerColor.getRGB(), true);
      renderer.paint = markerColor;
      renderer.renderAtOrigin(g2);

      g2.translate(-res.x, -res.y);
    }

    drawPopup(g2);
    if (panel.isLegendEnabled()) {
      drawLegend(g2);
    }
  }

  private int getMarkerSize(double mag) {
    int markerMag = Math.max((int) mag, 0);
    markerMag = Math.min(markerMag, markerSize.length);
    return markerSize[markerMag];
  }

  /**
   * Draw popup box.
   * 
   * @param g2 graphics 2D
   */
  private void drawPopup(Graphics2D g2) {
    if (hoverEvent == null) {
      return;
    }
    Origin origin = hoverEvent.getPreferredOrigin();
    GeoRange range = panel.getRange();
    Projection projection = panel.getProjection();
    List<String> text = generatePopupText(origin);

    FontMetrics fm = g2.getFontMetrics();
    int popupHeight = 2 * POPUP_PADDING;
    int popupWidth = 0;
    for (String string : text) {
      Rectangle2D bounds = fm.getStringBounds(string, g2);
      popupHeight += (int) (Math.ceil(bounds.getHeight()) + 2);
      popupWidth = Math.max(popupWidth, (int) (Math.ceil(bounds.getWidth()) + 2 * POPUP_PADDING));
    }

    int widthPx = panel.getGraphWidth();
    int heightPx = panel.getGraphHeight();
    int insetPx = panel.getInset();

    final Point2D.Double xy =
        projection.forward(new Point2D.Double(origin.getLongitude(), origin.getLatitude()));
    final double[] ext = range.getProjectedExtents(projection);
    final double dx = (ext[1] - ext[0]);
    final double dy = (ext[3] - ext[2]);
    final Point2D.Double res = new Point2D.Double();

    res.x = (((xy.x - ext[0]) / dx) * widthPx + insetPx);
    int maxX = widthPx - popupWidth + POPUP_PADDING;
    res.x = Math.min(res.x, maxX);

    res.y = ((1 - (xy.y - ext[2]) / dy) * heightPx + insetPx);
    if (res.y < (insetPx + popupHeight)) {
      res.y += popupHeight;
    }

    g2.translate(res.x, res.y);

    g2.setStroke(new BasicStroke(1.2f));
    g2.setColor(new Color(0, 0, 0, 128));
    g2.drawRect(0, -(popupHeight - POPUP_PADDING), popupWidth, popupHeight);
    g2.fillRect(0, -(popupHeight - POPUP_PADDING), popupWidth, popupHeight);

    g2.setColor(Color.WHITE);
    int baseY = POPUP_PADDING;
    for (String string : text) {
      g2.drawString(string, POPUP_PADDING, -baseY);
      Rectangle2D bounds = fm.getStringBounds(string, g2);
      baseY += (int) (Math.ceil(bounds.getHeight()) + 2);
    }
    g2.translate(-res.x, -res.y);

  }

  private List<String> generatePopupText(Origin origin) {
    List<String> text = new ArrayList<String>(3);

    Magnitude magElement = hoverEvent.getPreferredMagnitude();
    if (magElement != null) {
      String mag = String.format("%.2f %s at %.2f km depth", magElement.getMagnitude().getValue(),
          magElement.getType(), (origin.getDepth() / 1000));
      text.add(mag);
    }

    String date = Time.format(Time.STANDARD_TIME_FORMAT, new Date(origin.getTime()));
    text.add(date + " UTC");

    String description = hoverEvent.getDescription();
    if (description != null && !description.equals("")) {
      text.add(description);
    }

    return text;
  }

  /**
   * Mouse clicked event.
   * 
   * @see gov.usgs.volcanoes.swarm.map.MapLayer#mouseClicked(java.awt.event.MouseEvent)
   */
  public boolean mouseClicked(final MouseEvent e) {
    boolean handled = false;

    if (hoverEvent != null) {
      LOGGER.debug("Opening event {}", hoverEvent.getEventId());
      Swarm.openEvent(hoverEvent);
      handled = true;
      hoverEvent = null;
    }

    return handled;
  }

  /**
   * Setting changed.
   * 
   * @see gov.usgs.volcanoes.swarm.ConfigListener#settingsChanged()
   */
  public void settingsChanged() {
    LOGGER.debug("hypocenter plotter sees changed settings.");
    if (quakemlSource != null) {
      quakemlSource.stop();
    }

    HypocenterSource hypocenterSource = swarmConfig.getHypocenterSource();
    if (hypocenterSource == HypocenterSource.NONE) {
      events.clear();
      if (MapFrame.getInstance() != null) {
        MapFrame.getInstance().repaint();
      }
      return;
    }

    try {
      LOGGER.debug("New hypocenter source: {}", hypocenterSource);
      quakemlSource =
          new QuakemlSource(new URL(hypocenterSource.getUrl()), (long) REFRESH_INTERVAL);
      quakemlSource.start();
      quakemlSource.addObserver(this);
      update(quakemlSource);
    } catch (MalformedURLException ex) {
      LOGGER.error("Unable to load hypocenter URL.", ex);
    }
  }

  /**
   * Mouse moved event.
   * 
   * @see gov.usgs.volcanoes.swarm.map.MapLayer#mouseMoved(java.awt.event.MouseEvent)
   */
  public boolean mouseMoved(MouseEvent e) {
    if (events.size() < 1) {
      return false;
    }

    GeoRange range = panel.getRange();
    if (range == null) {
      return false;
    }
    Projection projection = panel.getProjection();
    if (projection == null) {
      return false;
    }

    int widthPx = panel.getGraphWidth();
    int heightPx = panel.getGraphHeight();
    int insetPx = panel.getInset();

    Iterator<Event> it = events.values().iterator();
    boolean handled = false;
    while (it.hasNext() && handled == false) {
      Event event = it.next();

      Origin origin = event.getPreferredOrigin();
      if (origin == null) {
        continue;
      }

      Point2D.Double originLoc = new Point2D.Double(origin.getLongitude(), origin.getLatitude());
      if (!range.contains(originLoc)) {
        continue;
      }

      Magnitude magnitude = event.getPreferredMagnitude();
      int markerDiameter = 5;
      if (magnitude != null) {
        markerDiameter = getMarkerSize(event.getPreferredMagnitude().getMagnitude().getValue());
      }
      final Rectangle r = new Rectangle(0, 0, markerDiameter, markerDiameter);

      final Point2D.Double xy =
          projection.forward(new Point2D.Double(origin.getLongitude(), origin.getLatitude()));
      final double[] ext = range.getProjectedExtents(projection);
      final double dx = (ext[1] - ext[0]);
      final double dy = (ext[3] - ext[2]);
      final Point2D.Double res = new Point2D.Double();
      res.x = (((xy.x - ext[0]) / dx) * widthPx + insetPx);
      res.y = ((1 - (xy.y - ext[2]) / dy) * heightPx + insetPx);

      r.translate((int) res.x, (int) res.y);
      if (r.contains(e.getPoint())) {
        LOGGER.debug("set hover event {}", event.publicId);
        hoverEvent = event;
        handled = true;
      } else if (event == hoverEvent) {
        LOGGER.debug("unset hover event {}", event.publicId);
        hoverEvent = null;
        handled = true;
      }
    }
    return handled;
  }

  /**
   * Add imported events.
   * 
   * @param eventSet quakeml events from file
   */
  public void add(EventSet eventSet) {
    importedEvents.putAll(eventSet);
    events.putAll(eventSet);
    GeoRange gr = new GeoRange();
    for (Event event : eventSet.values()) {
      Origin origin = event.getPreferredOrigin();
      if (origin != null) {
        Point2D.Double point = new Point2D.Double(origin.getLongitude(), origin.getLatitude());
        gr.includePoint(point, 0.1);
      }
    }
    gr.padPercent(.5, .5);
    MapFrame mapFrame = MapFrame.getInstance();
    if (mapFrame != null) {
      mapFrame.getMapPanel().setCenterAndScale(gr);
      mapFrame.setView(gr);
      mapFrame.setVisible(true);
      mapFrame.repaint();
    }
  }

  /**
   * Update.
   * 
   * @see gov.usgs.volcanoes.core.quakeml.QuakemlObserver#
   *      update(gov.usgs.volcanoes.core.quakeml.QuakemlSource)
   */
  public void update(QuakemlSource source) {
    events.clear();
    events.putAll(source.getEventSet());
    events.putAll(importedEvents);
    if (MapFrame.getInstance() != null) {
      MapFrame.getInstance().repaint();
    }
  }

  /**
   * Set visible.
   * 
   * @see gov.usgs.volcanoes.swarm.map.MapLayer#setVisible(boolean)
   */
  public void setVisible(boolean isVisible) {
    LOGGER.debug("Setting hypocenter update to {}", isVisible);
    if (quakemlSource != null) {
      quakemlSource.doUpdate(isVisible);
    }
  }

  /**
   * Remove event from layer.
   * 
   * @param publicId public id of event
   */
  public void remove(String publicId) {
    if (importedEvents.containsKey(publicId)) {
      events.remove(publicId);
    }
    if (events.containsKey(publicId)) {
      events.remove(publicId);
    }

  }

  /**
   * Draw legend for event plots.
   * 
   * @param g2 graphics2d
   */
  public void drawLegend(Graphics2D g2) {
    if (panel.getColorSetting() == ColorSetting.DEPTH) {
      drawDepthLegend(g2);
    } else {
      drawAgeLegend(g2);
    }
  }

  /**
   * Draw legend when events colors are plotted by depth.
   * 
   * @param g2 Grahics2D
   */
  public void drawDepthLegend(Graphics2D g2) {
    int heightPx = panel.getGraphHeight();
    int insetPx = panel.getInset();

    // legend background
    int recHeight = 90;
    int recWidth = 280;
    g2.setStroke(new BasicStroke(1.2f));
    g2.setColor(WHITE);
    g2.drawRect(insetPx, insetPx + heightPx - recHeight, recWidth, recHeight);
    g2.fillRect(insetPx, insetPx + heightPx - recHeight, recWidth, recHeight);

    g2.setStroke(new BasicStroke(2.0f));
    g2.setColor(BLACK);
    // Depth label
    float x = insetPx + 5;
    float y = insetPx + (heightPx - recHeight) + 15;
    g2.drawString("Depth (km):", x, y);
    y += 15;
    int size = getMarkerSize(3);
    // < 0 km
    Ellipse2D.Double circle = new Ellipse2D.Double(x, y - size / 2, size, size);
    g2.setColor(RED);
    g2.fill(circle);
    x += size + 2;
    g2.setColor(BLACK);
    g2.drawString("< 0", x, y + size / 2);
    x += 25;
    // 0-5 km
    circle = new Ellipse2D.Double(x, y - size / 2, size, size);
    g2.setColor(ORANGE);
    g2.fill(circle);
    x += size + 2;
    g2.setColor(BLACK);
    g2.drawString("0-5", x, y + size / 2);
    x += 30;
    // 5-13 km
    circle = new Ellipse2D.Double(x, y - size / 2, size, size);
    g2.setColor(YELLOW);
    g2.fill(circle);
    x += size + 2;
    g2.setColor(BLACK);
    g2.drawString("5-13", x, y + size / 2);
    x += 35;
    // 13-20 km
    circle = new Ellipse2D.Double(x, y - size / 2, size, size);
    g2.setColor(GREEN);
    g2.fill(circle);
    x += size + 2;
    g2.setColor(BLACK);
    g2.drawString("13-20", x, y + size / 2);
    x += 40;
    // 20-40 km
    circle = new Ellipse2D.Double(x, y - size / 2, size, size);
    g2.setColor(BLUE);
    g2.fill(circle);
    x += size + 2;
    g2.setColor(BLACK);
    g2.drawString("20-40", x, y + size / 2);
    x += 40;
    // 40+ km
    circle = new Ellipse2D.Double(x, y - size / 2, size, size);
    g2.setColor(PURPLE);
    g2.fill(circle);
    x += size + 2;
    g2.setColor(BLACK);
    g2.drawString("40+", x, y + size / 2);

    // Magnitudes
    x = insetPx + 5;
    y = insetPx + (heightPx - recHeight / 2) + 10;
    g2.setColor(BLACK);
    g2.drawString("Magnitude:", x, y);
    y += 15;
    for (int i = 1; i <= 7; i++) {
      size = getMarkerSize(i);
      g2.setColor(BLACK);
      g2.drawString(Integer.toString(i), x, y + 5);
      x += 10;
      circle = new Ellipse2D.Double(x, y - size / 2, size, size);
      g2.setColor(ORANGE);
      g2.fill(circle);
      x += size + 10;
    }
  }

  /**
   * Draw legend when events colors are plotted by age.
   * 
   * @param g2 graphics 2D.
   */
  public void drawAgeLegend(Graphics2D g2) {

    int heightPx = panel.getGraphHeight();
    int insetPx = panel.getInset();

    // legend background
    int recHeight = 50;
    int recWidth = 330;
    g2.setStroke(new BasicStroke(1.2f));
    g2.setColor(WHITE);
    g2.drawRect(insetPx, insetPx + heightPx - recHeight, recWidth, recHeight);
    g2.fillRect(insetPx, insetPx + heightPx - recHeight, recWidth, recHeight);

    int size = getMarkerSize(3);
    g2.setStroke(new BasicStroke(2.0f));
    // 1 hour ago key
    float x = insetPx + 5;
    float y = insetPx + (heightPx - recHeight) + 2;
    Ellipse2D.Double circle = new Ellipse2D.Double(x, y, size, size);
    g2.setColor(RED);
    g2.fill(circle);
    g2.setColor(BLACK);
    g2.drawString("< 1 Hour", x + size + 1, y + size);
    // 1 day ago key
    y = y + size + 5;
    circle = new Ellipse2D.Double(x, y, size, size);
    g2.setColor(ORANGE);
    g2.fill(circle);
    g2.setColor(BLACK);
    g2.drawString("< 1 Day", x + size + 1, y + size);
    // 1 week ago key
    y = y + size + 5;
    circle = new Ellipse2D.Double(x, y, size, size);
    g2.setColor(YELLOW);
    g2.fill(circle);
    g2.setColor(BLACK);
    g2.drawString("< 1 Week", x + size + 1, y + size);

    // Magnitudes
    x = x + size + 70;
    y = insetPx + (heightPx - recHeight) + 15;
    g2.drawString("Magnitude:", x, y);
    y = y + 15;
    for (int i = 1; i <= 7; i++) {
      size = getMarkerSize(i);
      g2.setColor(BLACK);
      g2.drawString(Integer.toString(i), x, y + 5);
      x += 10;
      circle = new Ellipse2D.Double(x, y - size / 2, size, size);
      g2.setColor(ORANGE);
      g2.fill(circle);
      x += size + 10;
    }
  }

}
