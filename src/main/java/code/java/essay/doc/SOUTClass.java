package code.java.essay.doc;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author cxr
 * @Date 2022/1/6 22:01
 */
public class SOUTClass {

    public static void main(String[] args) {
        List<Integer> res = new ArrayList<>();
        res.add(1);
        res.add(2);
        res.add(3);
        res.add(4);
        res.add(5);
        for (int i = 0; i < res.size(); i++) {
            System.out.println(res.get(i));
        }
    }
}
