package pl.autosklep;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class AutoSklepScreen extends Screen {

    private TextFieldWidget cmdField;
    private TextFieldWidget minField;
    private TextFieldWidget delayField;
    private TextFieldWidget clicksField;
    private ButtonWidget toggleButton;

    public AutoSklepScreen() {
        super(Text.literal("AutoSklep"));
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int cx = this.width / 2;
        int y = 55;

        cmdField = new TextFieldWidget(textRenderer, cx - 100, y, 200, 20, Text.literal("Komenda"));
        cmdField.setMaxLength(100);
        cmdField.setText(c.command);
        addDrawableChild(cmdField);

        y += 40;
        minField = new TextFieldWidget(textRenderer, cx - 100, y, 95, 20, Text.literal("Sloty"));
        minField.setMaxLength(2);
        minField.setText(String.valueOf(c.minCopperSlots));
        addDrawableChild(minField);

        clicksField = new TextFieldWidget(textRenderer, cx + 5, y, 95, 20, Text.literal("Klikniecia"));
        clicksField.setMaxLength(2);
        clicksField.setText(String.valueOf(c.copperClicks));
        addDrawableChild(clicksField);

        y += 40;
        delayField = new TextFieldWidget(textRenderer, cx - 100, y, 95, 20, Text.literal("Opoznienie"));
        delayField.setMaxLength(3);
        delayField.setText(String.valueOf(c.clickDelay));
        addDrawableChild(delayField);

        y += 40;
        toggleButton = ButtonWidget.builder(toggleText(), b -> {
            AutoSklepClient.setEnabled(!AutoSklepClient.enabled);
            b.setMessage(toggleText());
        }).dimensions(cx - 100, y, 200, 20).build();
        addDrawableChild(toggleButton);

        y += 25;
        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz i zamknij"), b -> close())
                .dimensions(cx - 100, y, 200, 20).build());
    }

    private Text toggleText() {
        return Text.literal("Mod: " + (AutoSklepClient.enabled ? "WŁĄCZONY" : "WYŁĄCZONY"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("AutoSklep"), cx, 20, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Komenda sklepu (bez /), np. sklep lub repair"),
                cx - 100, 43, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Min. slotow z miedzia (1-36)"),
                cx - 100, 83, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Klikniec w miedz"),
                cx + 5, 83, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Opoznienie klikniec (tick, 0 = najszybciej)"),
                cx - 100, 123, 0xAAAAAA);
    }

    private static int parse(String s, int def, int min, int max) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void close() {
        Config c = Config.get();
        String cmd = cmdField.getText().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (!cmd.isEmpty()) c.command = cmd;
        c.minCopperSlots = parse(minField.getText(), c.minCopperSlots, 1, 36);
        c.copperClicks = parse(clicksField.getText(), c.copperClicks, 1, 20);
        c.clickDelay = parse(delayField.getText(), c.clickDelay, 0, 100);
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
