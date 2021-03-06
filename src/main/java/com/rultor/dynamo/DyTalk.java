/**
 * Copyright (c) 2009-2014, rultor.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the rultor.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.rultor.dynamo;

import com.google.common.collect.Iterables;
import com.jcabi.aspects.Immutable;
import com.jcabi.aspects.Tv;
import com.jcabi.dynamo.AttributeUpdates;
import com.jcabi.dynamo.Item;
import com.jcabi.xml.StrictXML;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.rultor.spi.Talk;
import java.io.IOException;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.w3c.dom.Node;
import org.xembly.Directive;
import org.xembly.ImpossibleModificationException;
import org.xembly.Xembler;

/**
 * Talk in Dynamo.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 */
@Immutable
@ToString
@EqualsAndHashCode(of = "item")
public final class DyTalk implements Talk {

    /**
     * Item.
     */
    private final transient Item item;

    /**
     * Ctor.
     * @param itm Item
     */
    DyTalk(final Item itm) {
        this.item = itm;
    }

    @Override
    public Long number() throws IOException {
        return Long.parseLong(this.item.get(DyTalks.ATTR_NUMBER).getN());
    }

    @Override
    public String name() throws IOException {
        return this.item.get(DyTalks.HASH).getS();
    }

    @Override
    public XML read() throws IOException {
        return new XMLDocument(this.item.get(DyTalks.ATTR_XML).getS());
    }

    @Override
    public void modify(final Iterable<Directive> dirs) throws IOException {
        if (!Iterables.isEmpty(dirs)) {
            final XML xml = this.read();
            final Node node = xml.node();
            try {
                new Xembler(dirs).apply(node);
            } catch (final ImpossibleModificationException ex) {
                throw new IllegalStateException(
                    String.format(
                        "failed to apply %s to %s",
                        dirs.toString(), xml
                    ),
                    ex
                );
            }
            final String body = new StrictXML(
                new XMLDocument(node), Talk.SCHEMA
            ).toString();
            if (body.length() > Tv.FIFTY * Tv.THOUSAND) {
                throw new IllegalArgumentException("XML is too big");
            }
            this.item.put(
                new AttributeUpdates()
                    .with(DyTalks.ATTR_XML, body)
                    .with(DyTalks.ATTR_UPDATED, System.currentTimeMillis())
            );
        }
    }

    @Override
    public void active(final boolean yes) throws IOException {
        this.item.put(
            new AttributeUpdates()
                .with(DyTalks.ATTR_ACTIVE, yes)
                .with(DyTalks.ATTR_UPDATED, System.currentTimeMillis())
        );
    }

}
