package com.farmmacro.mixin;

import com.farmmacro.visual.NickHide;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v1.16.3 «Скрытие ника»: весь текст игры (GUI через GuiTextRenderState, ники над головой через drawInBatch)
 * в 26.1.2 проходит через {@code Font.prepareText(String|FormattedCharSequence, …)} (javap); ширина — через
 * {@code width(...)}, чтобы раскладка (Tab, центрирование) считалась по заменённому тексту.
 */
@Mixin(Font.class)
public abstract class NickFontMixin {

    @ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String farmmacro$nickStr(String s) {
        try { return NickHide.apply(s); } catch (Throwable t) { return s; }
    }

    @ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FormattedCharSequence farmmacro$nickSeq(FormattedCharSequence s) {
        try { return NickHide.apply(s); } catch (Throwable t) { return s; }
    }

    @ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String farmmacro$nickWidthStr(String s) {
        try { return NickHide.apply(s); } catch (Throwable t) { return s; }
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private FormattedCharSequence farmmacro$nickWidthSeq(FormattedCharSequence s) {
        try { return NickHide.apply(s); } catch (Throwable t) { return s; }
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
    private void farmmacro$nickWidthText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
        try {
            if (!NickHide.active() || text == null) return;
            String plain = text.getString();
            if (NickHide.apply(plain).equals(plain)) return;
            cir.setReturnValue(((Font) (Object) this).width(Language.getInstance().getVisualOrder(text)));
        } catch (Throwable ignored) {}
    }
}
