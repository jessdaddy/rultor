/**
 * Copyright (c) 2009-2013, rultor.com
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
package com.rultor.users;

import com.google.common.collect.Iterators;
import com.jcabi.aspects.Immutable;
import com.jcabi.aspects.Loggable;
import com.jcabi.aspects.Tv;
import com.jcabi.dynamo.Attributes;
import com.jcabi.dynamo.Item;
import com.jcabi.dynamo.Region;
import com.jcabi.dynamo.ScanValve;
import com.jcabi.urn.URN;
import com.rultor.spi.Dollars;
import com.rultor.spi.Receipt;
import com.rultor.spi.Time;
import java.util.Iterator;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.Validate;

/**
 * Receipts in DynamoDB.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 1.0
 * @checkstyle ClassDataAbstractionCoupling (500 lines)
 */
@Immutable
@ToString
@EqualsAndHashCode(of = { "region", "name" })
@Loggable(Loggable.DEBUG)
final class AwsReceipts implements Iterable<Receipt> {

    /**
     * Dynamo DB table name.
     */
    public static final String TABLE = "receipts";

    /**
     * Dynamo DB table column.
     */
    public static final String HASH = "hash";

    /**
     * Dynamo DB table column.
     */
    private static final String FIELD_PAYER = "payer";

    /**
     * Dynamo DB table column.
     */
    private static final String FIELD_BENEFICIARY = "beneficiary";

    /**
     * Dynamo DB table column.
     */
    private static final String FIELD_DETAILS = "details";

    /**
     * Dynamo DB table column.
     */
    private static final String FIELD_AMOUNT = "amount";

    /**
     * Dynamo DB table column.
     */
    private static final String FIELD_UNIT = "unit";

    /**
     * Dynamo.
     */
    private final transient Region region;

    /**
     * URN of the user.
     */
    private final transient URN name;

    /**
     * Public ctor.
     * @param reg Region in Dynamo
     * @param urn URN of the user
     */
    protected AwsReceipts(final Region reg, final URN urn) {
        this.region = reg;
        this.name = urn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Receipt> iterator() {
        return Iterators.concat(
            this.iterator(AwsReceipts.FIELD_PAYER),
            this.iterator(AwsReceipts.FIELD_BENEFICIARY)
        );
    }

    /**
     * Fetch by the provided field (if I'm there).
     * @param field Field name
     * @return Receipts
     */
    private Iterator<Receipt> iterator(final String field) {
        final Iterator<Item> items = this.region.table(AwsReceipts.TABLE)
            .frame()
            .where(field, this.name.toString())
            .through(
                new ScanValve()
                    .withLimit(Tv.FIFTY)
                    .withAttributeToGet(AwsReceipts.FIELD_AMOUNT)
                    .withAttributeToGet(AwsReceipts.FIELD_BENEFICIARY)
                    .withAttributeToGet(AwsReceipts.FIELD_DETAILS)
                    .withAttributeToGet(AwsReceipts.FIELD_PAYER)
                    .withAttributeToGet(AwsReceipts.FIELD_UNIT)
            )
            .iterator();
        return new Iterator<Receipt>() {
            @Override
            public boolean hasNext() {
                return items.hasNext();
            }
            @Override
            public Receipt next() {
                return AwsReceipts.toReceipt(items.next());
            }
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * AWS item to receipt.
     * @param item AWS item
     * @return Receipt
     */
    public static Receipt toReceipt(final Item item) {
        final String[] parts = item.get(AwsReceipts.HASH).getS().split(" ");
        return new Receipt.Simple(
            new Time(parts[0]),
            URN.create(item.get(AwsReceipts.FIELD_PAYER).getS()),
            URN.create(item.get(AwsReceipts.FIELD_BENEFICIARY).getS()),
            item.get(AwsReceipts.FIELD_DETAILS).getS(),
            new Dollars(
                Long.parseLong(item.get(AwsReceipts.FIELD_AMOUNT).getS())
            ),
            item.get(AwsReceipts.FIELD_UNIT).getS()
        );
    }

    /**
     * Add new receipt.
     * @param region Region we're in
     * @param receipt The receipt to add
     */
    public static void add(final Region region, final Receipt receipt) {
        Validate.isTrue(
            receipt.dollars().points() > 0,
            "receipt can be positive only, %s provided", receipt.dollars()
        );
        region.table(AwsReceipts.TABLE).put(
            new Attributes()
                .with(AwsReceipts.FIELD_UNIT, receipt.unit())
                .with(AwsReceipts.FIELD_PAYER, receipt.payer())
                .with(AwsReceipts.FIELD_BENEFICIARY, receipt.beneficiary())
                .with(AwsReceipts.FIELD_DETAILS, receipt.details())
                .with(AwsReceipts.FIELD_AMOUNT, receipt.dollars().points())
                .with(
                    AwsReceipts.HASH,
                    String.format("%s %s", receipt.date(), System.nanoTime())
                )
        );
    }

}