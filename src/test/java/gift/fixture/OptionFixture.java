package gift.fixture;

import gift.model.Option;

public class OptionFixture {

    public static Option 기본옵션(int quantity) {
        return new Option("TALL", quantity, null);
    }

}
