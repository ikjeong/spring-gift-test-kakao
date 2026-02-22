INSERT INTO member (id, name, email) VALUES (1, '보내는사람', 'sender@test.com');
INSERT INTO member (id, name, email) VALUES (2, '받는사람', 'receiver@test.com');
INSERT INTO category (id, name) VALUES (1, '교환권');
INSERT INTO product (id, name, price, image_url, category_id) VALUES (1, '아이스 아메리카노', 4500, 'https://example.com/image.png', 1);
INSERT INTO option (id, name, quantity, product_id) VALUES (1, 'TALL', 10, 1);
