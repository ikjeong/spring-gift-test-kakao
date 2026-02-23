package gift.fixture;

import gift.model.Member;

public class MemberFixture {

    public static Member 발신회원() {
        return new Member("보내는사람", "sender@test.com");
    }

    public static Member 수신회원() {
        return new Member("받는사람", "receiver@test.com");
    }
}
