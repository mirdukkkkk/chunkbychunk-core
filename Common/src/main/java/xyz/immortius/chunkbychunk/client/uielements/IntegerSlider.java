package xyz.immortius.chunkbychunk.client.uielements;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntegerSlider extends AbstractSliderButton {
    private final Supplier<Integer> getter;
    private final Consumer<Integer> setter;
    private final int minValue;
    private final int maxValue;
    private final Component valueName;

    public IntegerSlider(int x, int y, int width, int height, Component message, int minValue, int maxValue, Supplier<Integer> getter, Consumer<Integer> setter) {
        super(x, y, width, height, message, (double) (getter.get() - minValue) / (maxValue - minValue));
        this.valueName = message;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.getter = getter;
        this.setter = setter;
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.empty().append(valueName).append(": ").append(String.valueOf(getter.get())));
    }

    @Override
    protected void applyValue() {
        setter.accept(Mth.floor(Mth.clampedLerp(minValue, maxValue, this.value)));
    }

    public void setValue(int newValue) {
        if (newValue >= minValue && newValue <= maxValue) {
            setter.accept(newValue);
            this.value = (double) (newValue - minValue) / (maxValue - minValue);
            updateMessage();
        }
    }
}