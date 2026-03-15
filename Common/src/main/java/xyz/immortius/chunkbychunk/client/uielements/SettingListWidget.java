package xyz.immortius.chunkbychunk.client.uielements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xyz.immortius.chunkbychunk.common.ChunkByChunkConstants;
import xyz.immortius.chunkbychunk.config.ChunkByChunkConfig;
import xyz.immortius.chunkbychunk.config.system.*;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SettingListWidget extends ContainerObjectSelectionList<SettingListWidget.SettingEntry> {

    private EditBox lastFocused = null;
    private final int rowWidth;

    public SettingListWidget(Minecraft minecraft, Screen parent, int width, int top, int bottom, int rowWidth) {
        super(minecraft, width, bottom - top, top, 22);
        this.rowWidth = rowWidth;

        ConfigMetadata metadata = MetadataBuilder.build(ChunkByChunkConfig.class);
        ChunkByChunkConfig defaultConfig = new ChunkByChunkConfig();

        for (SectionMetadata section : metadata.getSections().values()) {
            Object defaultSection = section.getSectionObject(defaultConfig);
            Object configSection = section.getSectionObject(ChunkByChunkConfig.get());
            this.addEntry(new SectionTitleEntry(section.getDisplayName()));
            for (FieldMetadata<?> field : section.getFields().values()) {
                if (field instanceof BooleanFieldMetadata boolField) {
                    this.addEntry(new BooleanEntry(field.getDisplayName(), () -> boolField.getValue(configSection), (x) -> boolField.setValue(configSection, x), boolField.getValue(defaultSection)));
                } else if (field instanceof EnumFieldMetadata enumField) {
                    this.addEntry(new EnumEntry(enumField.getDisplayName(), enumField.enumType(), () -> enumField.getValue(configSection), (x) -> enumField.setValue(configSection, x), enumField.getValue(defaultSection)));
                } else if (field instanceof IntFieldMetadata intField) {
                    Integer defaultValue = intField.getValue(defaultSection);
                    if (intField.getMaxValue() - intField.getMinValue() > 256) {
                        this.addEntry(new ExtendedIntegerEntry(field.getDisplayName(), intField.getMinValue(), intField.getMaxValue(), () -> intField.getValue(configSection), (x) -> intField.setValue(configSection, x), defaultValue));
                    } else {
                        this.addEntry(new IntegerEntry(field.getDisplayName(), intField.getMinValue(), intField.getMaxValue(), () -> intField.getValue(configSection), (x) -> intField.setValue(configSection, x), defaultValue));
                    }
                } else if (field instanceof StringFieldMetadata stringField) {
                    this.addEntry(new StringEntry(field.getDisplayName(), () -> stringField.getValue(configSection), (x) -> stringField.setValue(configSection, x), stringField.getValue(defaultSection)));
                }
            }
        }
    }

    @Override
    public int getRowWidth() {
        return rowWidth;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.getX() + this.width / 2 + getRowWidth() / 2 + 4;
    }

    public void tick() {
        this.children().forEach(SettingEntry::tick);
    }

    public void reset() {
        children().forEach(SettingEntry::reset);
    }

    public class SectionTitleEntry extends SettingEntry {
        private final Component displayName;

        public SectionTitleEntry(Component displayName) {
            this.displayName = displayName;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovered, float delta) {
            graphics.drawString(SettingListWidget.this.minecraft.font, this.displayName, left + 12, top + 6, 0xFFFFFF, true);
        }

        @Override
        public List<? extends NarratableEntry> narratables() { return Collections.emptyList(); }
        @Override
        public List<? extends GuiEventListener> children() { return Collections.emptyList(); }
    }

    public class IntegerEntry extends AbstractWidgetEntry<IntegerSlider> {
        private final Consumer<Integer> setter;
        private final Integer defaultValue;

        public IntegerEntry(Component displayName, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter, Integer defaultValue) {
            super(new IntegerSlider(0, 0, getRowWidth(), 20, displayName, min, max, getter, setter));
            this.setter = setter;
            this.defaultValue = defaultValue;
        }

        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }
    }

    public class StringEntry extends AbstractWidgetEntry<EditBox> {
        private final Component displayName;
        private final Consumer<String> setter;
        private final String defaultValue;

        public StringEntry(Component displayName, Supplier<String> getter, Consumer<String> setter, String defaultValue) {
            super(new EditBox(SettingListWidget.this.minecraft.font, 0, 0, getRowWidth(), 20, displayName));
            this.displayName = displayName;
            this.defaultValue = defaultValue;
            this.setter = setter;
            widget.setValue(getter.get());
            widget.setResponder(setter);
        }

        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovered, float delta) {
            int labelLength = SettingListWidget.this.minecraft.font.width(this.displayName);
            graphics.drawString(SettingListWidget.this.minecraft.font, this.displayName, left, top + 6, 0xFFFFFF, true);
            widget.setX(left + labelLength + 6);
            widget.setWidth(getRowWidth() - labelLength - 6);
            widget.setY(top);
            widget.render(graphics, 0, 0, delta); // mouseX/Y здесь не важны для EditBox в списке
        }
    }

    public class BooleanEntry extends AbstractWidgetEntry<CycleButton<Boolean>> {
        private final Consumer<Boolean> setter;
        private final Boolean defaultValue;

        public BooleanEntry(Component displayName, Supplier<Boolean> getter, Consumer<Boolean> setter, Boolean defaultValue) {
            super(CycleButton.booleanBuilder(Component.translatable("gui.yes"), Component.translatable("gui.no"))
                    .withInitialValue(getter.get())
                    .create(0, 0, getRowWidth(), 20, displayName, (btn, val) -> setter.accept(val)));
            this.setter = setter;
            this.defaultValue = defaultValue;
        }

        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }
    }

    public class ExtendedIntegerEntry extends AbstractWidgetEntry<EditBox> {
        private final Component displayName;
        private final Consumer<Integer> setter;
        private final Integer defaultValue;

        public ExtendedIntegerEntry(Component displayName, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter, Integer defaultValue) {
            super(new EditBox(SettingListWidget.this.minecraft.font, 0, 0, getRowWidth(), 20, displayName));
            this.displayName = displayName;
            this.setter = setter;
            this.defaultValue = defaultValue;
            widget.setFilter(x -> {
                if (x.isEmpty() || "-".equals(x)) return true;
                try { int val = Integer.parseInt(x); return val >= min && val <= max; } catch (NumberFormatException e) { return false; }
            });
            widget.setValue(getter.get().toString());
            widget.setResponder(val -> {
                try { setter.accept(val.isEmpty() || "-".equals(val) ? 0 : Integer.parseInt(val)); } catch (NumberFormatException ignored) {}
            });
        }

        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue.toString());
        }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovered, float delta) {
            int labelLength = SettingListWidget.this.minecraft.font.width(this.displayName);
            graphics.drawString(SettingListWidget.this.minecraft.font, this.displayName, left, top + 6, 0xFFFFFF, true);
            widget.setX(left + labelLength + 6);
            widget.setWidth(getRowWidth() - labelLength - 6);
            widget.setY(top);
            widget.render(graphics, 0, 0, delta);
        }
    }

    public class EnumEntry extends AbstractWidgetEntry<CycleButton<Enum<?>>> {
        private final Consumer<Enum<?>> setter;
        private final Enum<?> defaultValue;

        public EnumEntry(Component displayName, Class<? extends Enum<?>> type, Supplier<Enum<?>> getter, Consumer<Enum<?>> setter, Enum<?> defaultValue) {
            super(new CycleButton.Builder<Enum<?>>((x) -> Component.translatable("enumvalue.chunkbychunk." + type.getSimpleName() + "." + x.name()))
                    .withValues(type.getEnumConstants()).withInitialValue(getter.get())
                    .create(0, 0, getRowWidth(), 20, displayName, (btn, val) -> setter.accept(val)));
            this.setter = setter;
            this.defaultValue = defaultValue;
        }

        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }
    }

    public abstract class AbstractWidgetEntry<T extends AbstractWidget> extends SettingEntry {
        protected final T widget;

        public AbstractWidgetEntry(T widget) { this.widget = widget; }

        @Override
        public void renderContent(GuiGraphics graphics, int top, int left, boolean hovered, float delta) {
            widget.setX(left);
            widget.setY(top);
            widget.render(graphics, 0, 0, delta);
        }

        @Override
        public List<? extends NarratableEntry> narratables() { return Collections.singletonList(widget); }
        @Override
        public List<? extends GuiEventListener> children() { return Collections.singletonList(widget); }

        @Override
        public boolean mouseClicked(double x, double y, int btn) {
            if (lastFocused != null && lastFocused != widget) lastFocused.setFocused(false);
            if (widget.mouseClicked(x, y, btn)) {
                if (widget instanceof EditBox eb) { lastFocused = eb; eb.setFocused(true); }
                return true;
            }
            return false;
        }
    }

    public static abstract class SettingEntry extends ContainerObjectSelectionList.Entry<SettingEntry> {
        public void tick() {}
        public void reset() {}
        public abstract void renderContent(GuiGraphics graphics, int top, int left, boolean hovered, float delta);

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float delta) {
            renderContent(graphics, top, left, hovered, delta);
        }
    }
}