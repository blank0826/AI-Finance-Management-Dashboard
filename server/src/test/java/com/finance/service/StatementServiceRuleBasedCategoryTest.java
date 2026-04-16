package com.finance.service;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.finance.entity.Transaction;

class StatementServiceRuleBasedCategoryTest {

    @Test
    void ruleBasedCategory_foodAndShopping_andFallback() throws Exception {
        StatementService svc = new StatementService();

        Method m = StatementService.class.getDeclaredMethod("ruleBasedCategory", String.class);
        m.setAccessible(true);

        Object res1 = m.invoke(svc, "Zomato order #12345");
        assertEquals(Transaction.Category.FOOD_AND_DINING, res1);

        Object res2 = m.invoke(svc, "Amazon purchase: headphones");
        assertEquals(Transaction.Category.SHOPPING, res2);

        Object res3 = m.invoke(svc, "Some unknown merchant XYZ");
        assertEquals(Transaction.Category.OTHER, res3);
    }
}
