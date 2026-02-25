Feature: 상품 관리

  Background:
    Given 카테고리가 등록되어 있다

  Scenario: 상품을 생성하면 목록에서 조회된다
    When "아이스 아메리카노" 상품을 4500원, 이미지 "https://example.com/image.png"으로 해당 카테고리에 등록한다
    Then 상품 등록이 성공한다
    And 상품 목록에 "아이스 아메리카노"이 4500원, 이미지 "https://example.com/image.png"으로 해당 카테고리에 포함되어 있다

  Scenario: 존재하지 않는 카테고리로 상품을 등록하면 실패한다
    When 존재하지 않는 카테고리로 상품을 등록한다
    Then 상품 등록이 실패한다
