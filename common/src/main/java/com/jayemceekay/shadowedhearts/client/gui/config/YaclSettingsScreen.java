package com.jayemceekay.shadowedhearts.client.gui.config;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.gui.OptionListWidget;
import dev.isxander.yacl3.gui.SearchFieldWidget;
import dev.isxander.yacl3.gui.WidgetAndType;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class YaclSettingsScreen {
    private YaclSettingsScreen() {}

    public static Screen create(Screen parent, ModConfigSpec spec, Component title, BooleanSupplier canEdit) {
        if (!canEdit.getAsBoolean()) return parent;
        var session = new SpecEditSession(spec, canEdit);
        return new FileScreen(build(session, title), parent, canEdit);
    }

    public static boolean isSettingsScreen(Screen screen) {
        return screen instanceof FileScreen;
    }

    static final class FileScreen extends YACLScreen {
        private final BooleanSupplier canEdit;
        private boolean returningFromList;
        private int listOpenedWidth, listOpenedHeight;
        private SearchFieldWidget searchField;
        private OptionListWidget results;
        private String displayedQuery = "";

        FileScreen(YetAnotherConfigLib config, Screen parent, BooleanSupplier canEdit) {
            super(config, parent);
            this.canEdit = canEdit;
        }

        boolean canEdit() { return canEdit.getAsBoolean(); }

        @Override
        protected void init() {
            super.init();
            for (var child : children()) {
                if (child instanceof SearchFieldWidget search) {
                    search.setMaxLength(256);
                    searchField = search;
                    displayedQuery = search.getValue();
                } else if (child instanceof WidgetAndType<?> holder && holder.getType() instanceof OptionListWidget list) {
                    results = list;
                }
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // Leave YACL's search responder in charge of filtering, then reveal its first result before drawing.
            if (searchField != null && results != null && !displayedQuery.equals(searchField.getValue())) {
                displayedQuery = searchField.getValue();
                results.setScrollAmount(0);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        void openList(StringListController controller) {
            if (!canEdit()) {
                onClose();
                return;
            }
            returningFromList = true;
            listOpenedWidth = width;
            listOpenedHeight = height;
            minecraft.setScreen(new StringListEditScreen(this, controller));
        }

        @Override
        protected void repositionElements() {
            // Minecraft repositions the parent on return. Reuse the search, scroll, and focused row
            // when the existing widgets still fit; a changed GUI scale needs a fresh layout.
            boolean fits = returningFromList && width == listOpenedWidth && height == listOpenedHeight;
            returningFromList = false;
            if (!fits) super.repositionElements();
        }

        @Override
        public void tick() {
            if (canEdit()) super.tick();
            else onClose();
        }

        @Override
        public void finishOrSave() {
            if (!canEdit()) {
                onClose();
                return;
            }
            try {
                super.finishOrSave();
            } catch (SpecEditSession.SaveRejected failure) {
                if (!canEdit()) onClose();
                else setSaveButtonMessage(failure.title.copy().withStyle(ChatFormatting.RED), failure.detail);
            }
        }
    }

    private static YetAnotherConfigLib build(SpecEditSession session, Component title) {
        // Search in YACL 3.8 is category-local. All topics share one category.
        ConfigCategory.Builder category = ConfigCategory.createBuilder().name(title);
        SettingsLayout.groups(session.spec).forEach((topic, values) -> {
            OptionGroup.Builder group = OptionGroup.createBuilder()
                    .name(SettingsLayout.title(topic))
                    .description(OptionDescription.of(SettingsLayout.description(topic)))
                    .collapsed(true);
            for (var value : values) {
                if (value.getSpec() instanceof ModConfigSpec.ListValueSpec listSpec) {
                    group.option(listOption(value, listSpec, session));
                } else {
                    group.option(option(value, session));
                }
            }
            category.group(group.build());
        });
        return YetAnotherConfigLib.createBuilder()
                .title(title)
                .category(category.build())
                .save(session::save)
                .build();
    }

    private static Option<?> option(ModConfigSpec.ConfigValue<?> value, SpecEditSession session) {
        return switch (value) {
            case ModConfigSpec.BooleanValue toggle -> binding(toggle, session)
                    .controller(TickBoxControllerBuilder::create).build();
            case ModConfigSpec.IntValue integer -> {
                ModConfigSpec.Range<Integer> range = integer.getSpec().getRange();
                yield binding(integer, session).controller(opt -> IntegerFieldControllerBuilder.create(opt)
                        .range(range.getMin(), range.getMax())).build();
            }
            case ModConfigSpec.DoubleValue decimal -> {
                ModConfigSpec.Range<Double> range = decimal.getSpec().getRange();
                yield binding(decimal, session).customController(opt -> new PreciseDoubleField(opt,
                        range.getMin(), range.getMax())).build();
            }
            case ModConfigSpec.EnumValue<?> choice -> enumOption(choice, session);
            default -> stringOption(value, session);
        };
    }

    private static <E extends Enum<E>> Option<E> enumOption(ModConfigSpec.EnumValue<E> value, SpecEditSession session) {
        return binding(value, session).controller(opt -> EnumControllerBuilder.create(opt)
                .enumClass(value.getDefault().getDeclaringClass()).formatValue(SettingsLayout::enumValue)).build();
    }

    @SuppressWarnings("unchecked")
    private static Option<String> stringOption(ModConfigSpec.ConfigValue<?> value, SpecEditSession session) {
        if (!(value.getDefault() instanceof String))
            throw new IllegalArgumentException("Unsupported setting: " + value.getPath());
        return binding((ModConfigSpec.ConfigValue<String>) value, session)
                .controller(StringControllerBuilder::create).build();
    }

    @SuppressWarnings("unchecked")
    private static Option<List<String>> listOption(ModConfigSpec.ConfigValue<?> value,
                                                    ModConfigSpec.ListValueSpec listSpec, SpecEditSession session) {
        // The three schemas use strings for every list, including lists empty by default.
        var newElement = listSpec.getNewElementSupplier();
        if (newElement == null || !(newElement.get() instanceof String))
            throw new IllegalArgumentException("Unsupported list element: " + value.getPath());
        var strings = (ModConfigSpec.ConfigValue<List<String>>) value;
        return binding(strings, session)
                .customController(opt -> new StringListController(opt, listSpec, strings.getDefault(), () -> (String) newElement.get()))
                .build();
    }

    private static <T> Option.Builder<T> binding(ModConfigSpec.ConfigValue<T> value, SpecEditSession session) {
        return Option.<T>createBuilder().name(SettingsLayout.label(value))
                .description(OptionDescription.of(SettingsLayout.optionDescription(value))).binding(session.bind(value));
    }
}
