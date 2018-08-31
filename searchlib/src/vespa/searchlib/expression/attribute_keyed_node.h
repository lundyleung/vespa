// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attributenode.h"

namespace search::expression {

/**
 * Extract map value from attribute for the map key specified in the
 * grouping expression.
 */
class AttributeKeyedNode : public AttributeNode
{
    using IAttributeVector = search::attribute::IAttributeVector;
    vespalib::string        _keyAttributeName;
    vespalib::string        _valueAttributeName;
    vespalib::string        _key;
    const IAttributeVector *_keyAttribute;

    class KeyHandler;
    template <typename VT, typename KT = VT> class KeyHandlerT;
    class IntegerKeyHandler;
    class FloatKeyHandler;
    class StringKeyHandler;
    class EnumKeyHandler;
    class ValueHandler;
    template <typename VT, typename RNT> class ValueHandlerT;
    template <typename RNT> class IntegerValueHandler;
    class FloatValueHandler;
    class StringValueHandler;
    class EnumValueHandler;

    void setupAttributeNames();
    template <typename RN>
    void prepareIntValues(std::unique_ptr<KeyHandler> keyHandler, const IAttributeVector &attribute, IAttributeVector::largeint_t undefinedValue);
    std::unique_ptr<KeyHandler> makeKeyHandler();
    void cleanup() override;
    void wireAttributes(const search::attribute::IAttributeContext & attrCtx) override;
    void onPrepare(bool preserveAccurateTypes) override;
public:
    AttributeKeyedNode();
    AttributeKeyedNode(vespalib::stringref name);
    AttributeKeyedNode(const AttributeKeyedNode &);
    AttributeKeyedNode(AttributeKeyedNode &&) = delete;
    ~AttributeKeyedNode() override;
    AttributeKeyedNode &operator=(const AttributeKeyedNode &rhs);
    AttributeKeyedNode &operator=(AttributeKeyedNode &&rhs) = delete;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    bool isKeyed() const override { return true; }
};

}
