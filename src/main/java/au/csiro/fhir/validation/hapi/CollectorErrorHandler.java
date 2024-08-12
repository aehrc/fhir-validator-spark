package au.csiro.fhir.validation.hapi;

import au.csiro.fhir.validation.ValidationResult;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.parser.*;
import ca.uhn.fhir.parser.json.BaseJsonLikeValue.ScalarType;
import ca.uhn.fhir.parser.json.BaseJsonLikeValue.ValueType;
import ca.uhn.fhir.util.UrlUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.nonNull;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2023 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

/**
 * Parser error handler which throws a {@link DataFormatException} any time an
 * issue is found while parsing.
 *
 * @see IParser#setParserErrorHandler(IParserErrorHandler)
 * @see FhirContext#setParserErrorHandler(IParserErrorHandler)
 */
public class CollectorErrorHandler implements IParserErrorHandler {

    @Nonnull
    final List<ValidationResult.Issue> issues = new ArrayList<>();

    @Nonnull
    public ValidationResult toValidationResult() {
        return new ValidationResult(issues);
    }

    @Override
    public void containedResourceWithNoId(IParseLocation theLocation) {
        addError(Msg.code(1819), "Resource has contained child resource with no ID", theLocation);
    }


    @Override
    public void incorrectJsonType(IParseLocation theLocation, String theElementName, ValueType theExpected, ScalarType theExpectedScalarType, ValueType theFound, ScalarType theFoundScalarType) {
        String message = LenientErrorHandler.createIncorrectJsonTypeMessage(theElementName, theExpected, theExpectedScalarType, theFound, theFoundScalarType);
        addError(Msg.code(1820), message, theLocation);
    }

    @Override
    public void invalidValue(IParseLocation theLocation, String theValue, String theError) {
        addError(Msg.code(1821), "Invalid attribute value \"" + UrlUtil.sanitizeUrlPart(theValue) + "\": " + theError, theLocation);
    }

    @Override
    public void missingRequiredElement(IParseLocation theLocation, String theElementName) {
        String message = createMissingRequiredElementMessage(theLocation, theElementName);
        addError(Msg.code(1822), message, theLocation);
    }

    @Override
    public void unexpectedRepeatingElement(IParseLocation theLocation, String theElementName) {
        addError(Msg.code(1823), "Multiple repetitions of non-repeatable element '" + theElementName + "' found during parse", theLocation);
        throw new DataFormatException(Msg.code(1823) + describeLocation(theLocation) + "Multiple repetitions of non-repeatable element '" + theElementName + "' found during parse");
    }

    @Override
    public void unknownAttribute(IParseLocation theLocation, String theAttributeName) {
        addError(Msg.code(1824), "Unknown attribute '" + theAttributeName + "' found during parse", theLocation);
    }

    @Override
    public void unknownElement(IParseLocation theLocation, String theElementName) {
        addError(Msg.code(1825), "Unknown element '" + theElementName + "' found during parse", theLocation);
    }

    @Override
    public void unknownReference(IParseLocation theLocation, String theReference) {
        addError(Msg.code(1826), "Resource has invalid reference: " + theReference, theLocation);
    }

    @Override
    public void extensionContainsValueAndNestedExtensions(IParseLocation theLocation) {
        addError(Msg.code(1827), "Extension contains both a value and nested extensions", theLocation);
    }

    private String describeLocation(IParserErrorHandler.IParseLocation theLocation) {
        if (theLocation == null) {
            return "";
        } else {
            return theLocation.toString() + " ";
        }
    }

    private void addError(@Nonnull String code, @Nonnull String message, @Nullable final IParseLocation theLocation) {
        issues.add(ValidationResult.Issue.builder()
                .level("error")
                .type(code.trim())
                .message(message)
                .location(nonNull(theLocation) ? theLocation.toString() : null).build());
    }

    @NotNull
    private static String createMissingRequiredElementMessage(IParseLocation theLocation, String theElementName) {
        StringBuilder b = new StringBuilder();
        b.append("Resource is missing required element '");
        b.append(theElementName);
        b.append("'");
        if (theLocation != null) {
            b.append(" in parent element '");
            b.append(theLocation.getParentElementName());
            b.append("'");
        }
        return b.toString();
    }

}
