package com.debttracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;

class DebtTrackerOverlay extends OverlayPanel
{
	private static final Color BAR_PAYING = new Color(214, 158, 46);
	private static final Color BAR_PAID = new Color(56, 161, 105);
	private static final Color BAR_BACKGROUND = new Color(61, 56, 48);
	private static final Color TEXT_LOSS = new Color(240, 90, 80);
	private static final Color TEXT_WARNING = new Color(250, 200, 80);

	private final DebtTrackerPlugin plugin;

	@Inject
	DebtTrackerOverlay(DebtTrackerPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.hasDebt())
		{
			return null;
		}

		LineComponent title = LineComponent.builder().left(plugin.getDebtName()).leftColor(Color.WHITE).build();
		BufferedImage icon = plugin.getItemIcon();
		panelComponent.getChildren().add(icon == null ? title : SplitComponent.builder()
			.first(new ImageComponent(icon))
			.second(title)
			.orientation(ComponentOrientation.HORIZONTAL)
			.gap(new Point(4, 0))
			.build());

		long amount = plugin.getDebtAmount();
		if (plugin.isAmountInvalid() || amount <= 0)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(plugin.isAmountInvalid() ? "Check debt amount" : "Set a debt amount")
				.leftColor(TEXT_WARNING)
				.build());
			return super.render(graphics);
		}

		long profit = plugin.getProfit();
		boolean paid = profit >= amount;

		ProgressBarComponent bar = new ProgressBarComponent();
		bar.setMaximum(amount);
		bar.setValue(Math.max(0, Math.min(profit, amount)));
		bar.setForegroundColor(paid ? BAR_PAID : BAR_PAYING);
		bar.setBackgroundColor(BAR_BACKGROUND);
		panelComponent.getChildren().add(bar);

		panelComponent.getChildren().add(LineComponent.builder()
			.left(GpFormat.format(profit) + " / " + GpFormat.format(amount))
			.leftColor(profit < 0 ? TEXT_LOSS : Color.WHITE)
			.build());
		panelComponent.getChildren().add(paid
			? LineComponent.builder().left("Paid off!").leftColor(BAR_PAID).right("+" + GpFormat.format(profit - amount)).rightColor(BAR_PAID).build()
			: LineComponent.builder().left("Left:").right(GpFormat.format(amount - profit)).build());

		if (!plugin.isBankSynced())
		{
			panelComponent.getChildren().add(LineComponent.builder().left("Open bank to sync").leftColor(TEXT_WARNING).build());
		}

		return super.render(graphics);
	}
}
