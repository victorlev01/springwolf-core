// SPDX-License-Identifier: Apache-2.0
package io.github.springwolf.core.asyncapi.scanners.common;

import io.github.springwolf.asyncapi.v3.bindings.MessageBinding;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessageHeaders;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessageObject;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessagePayload;
import io.github.springwolf.asyncapi.v3.model.channel.message.MessageReference;
import io.github.springwolf.asyncapi.v3.model.schema.MultiFormatSchema;
import io.github.springwolf.asyncapi.v3.model.schema.SchemaReference;
import io.github.springwolf.core.asyncapi.components.ComponentsService;
import io.github.springwolf.core.asyncapi.components.headers.AsyncHeadersBuilder;
import io.github.springwolf.core.asyncapi.scanners.bindings.BindingFactory;
import io.github.springwolf.core.asyncapi.scanners.common.payload.PayloadClassExtractor;
import io.github.springwolf.core.asyncapi.scanners.common.utils.AnnotationScannerUtil;
import io.github.springwolf.core.asyncapi.scanners.operations.annotations.SpringAnnotationClassLevelOperationsScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static io.github.springwolf.core.asyncapi.scanners.common.MessageHelper.toMessagesMap;
import static io.github.springwolf.core.asyncapi.scanners.common.MessageHelper.toOperationsMessagesMap;
import static java.util.stream.Collectors.toSet;

@Slf4j
@RequiredArgsConstructor
public abstract class ClassLevelAnnotationScanner<
        ClassAnnotation extends Annotation, MethodAnnotation extends Annotation> {

    protected final Class<ClassAnnotation> classAnnotationClass;
    protected final Class<MethodAnnotation> methodAnnotationClass;
    protected final BindingFactory<ClassAnnotation> bindingFactory;
    protected final AsyncHeadersBuilder asyncHeadersBuilder;
    protected final PayloadClassExtractor payloadClassExtractor;
    protected final ComponentsService componentsService;

    protected enum MessageType {
        CHANNEL,
        OPERATION
    }

    protected boolean isClassAnnotated(Class<?> component) {
        return AnnotationScannerUtil.findAnnotation(classAnnotationClass, component) != null;
    }

    protected Set<Method> getAnnotatedMethods(Class<?> clazz) {
        log.debug(
                "Scanning class \"{}\" for @\"{}\" annotated methods",
                clazz.getName(),
                methodAnnotationClass.getName());

        return Arrays.stream(clazz.getDeclaredMethods())
                .filter(method -> !method.isBridge())
                .filter(method -> AnnotationUtils.findAnnotation(method, methodAnnotationClass) != null)
                .collect(toSet());
    }

    protected Map<String, MessageReference> buildMessages(
            ClassAnnotation classAnnotation,
            Set<Method> methods,
            SpringAnnotationClassLevelOperationsScanner.MessageType messageType) {
        Set<MessageObject> messages = methods.stream()
                .map((Method method) -> {
                    Class<?> payloadType = payloadClassExtractor.extractFrom(method);
                    return buildMessage(classAnnotation, payloadType);
                })
                .collect(toSet());

        if (messageType == MessageType.OPERATION) {
            String channelName = bindingFactory.getChannelName(classAnnotation);
            return toOperationsMessagesMap(channelName, messages);
        }
        return toMessagesMap(messages);
    }

    protected MessageObject buildMessage(ClassAnnotation classAnnotation, Class<?> payloadType) {
        Map<String, MessageBinding> messageBinding = bindingFactory.buildMessageBinding(classAnnotation);
        String modelName = componentsService.registerSchema(payloadType);
        String headerModelName = componentsService.registerSchema(asyncHeadersBuilder.buildHeaders(payloadType));

        MessagePayload payload = MessagePayload.of(MultiFormatSchema.builder()
                .schema(SchemaReference.fromSchema(modelName))
                .build());

        MessageObject message = MessageObject.builder()
                .messageId(payloadType.getName())
                .name(payloadType.getName())
                .title(payloadType.getSimpleName())
                .description(null)
                .payload(payload)
                .headers(MessageHeaders.of(MessageReference.toSchema(headerModelName)))
                .bindings(messageBinding)
                .build();

        this.componentsService.registerMessage(message);
        return message;
    }
}
