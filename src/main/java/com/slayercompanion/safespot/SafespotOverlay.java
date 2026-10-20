package com.slayercompanion.safespot;

import com.slayercompanion.data.Safespot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Outlines the current location's safespot tiles: solid when verified, dashed when not. */
public class SafespotOverlay extends Overlay
{
	private static final Color VERIFIED = new Color(0, 200, 0);
	private static final Color UNVERIFIED = new Color(255, 200, 0);
	private static final Stroke SOLID = new BasicStroke(2f);
	private static final Stroke DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
		10f, new float[]{6f, 4f}, 0f);
	private static final int MAX_DRAW_DISTANCE = 32;

	private final Client client;
	private final SafespotStore store;
	private volatile String locationId;

	@Inject
	public SafespotOverlay(Client client, SafespotStore store)
	{
		this.client = client;
		this.store = store;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
	}

	public void setLocationId(String locationId)
	{
		this.locationId = locationId;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		String loc = locationId;
		if (loc == null || client.getLocalPlayer() == null
			|| !client.getLocalPlayer().getWorldView().isTopLevel())
		{
			return null;
		}
		WorldPoint playerPos = client.getLocalPlayer().getWorldLocation();
		for (Safespot spot : store.forLocation(loc))
		{
			// Spots are stored in template (non-instance) coordinates; map to the real scene like Ground Markers.
			for (WorldPoint wp : WorldPoint.toLocalInstance(client.getTopLevelWorldView(), spot.getTile().toWorldPoint()))
			{
				if (wp.getPlane() != client.getTopLevelWorldView().getPlane() || wp.distanceTo(playerPos) >= MAX_DRAW_DISTANCE)
				{
					continue;
				}
				LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), wp);
				if (lp == null)
				{
					continue;
				}
				Polygon poly = canvasPoly(lp);
				if (poly != null)
				{
					Color c = spot.isVerified() ? VERIFIED : UNVERIFIED;
					OverlayUtil.renderPolygon(graphics, poly, c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 40),
						spot.isVerified() ? SOLID : DASHED);
				}
			}
		}
		return null;
	}

	private Polygon canvasPoly(LocalPoint lp)
	{
		return Perspective.getCanvasTilePoly(client, lp);
	}
}
