import { ARTICLES_DATA } from '../services/content-data';

describe('Sprint S13 Care, Health & Guides Business Rules', () => {
  test('ARTICLES_DATA contains all 3 required guide articles', () => {
    expect(ARTICLES_DATA['puppy-nutrition-0-2-mo']).toBeDefined();
    expect(ARTICLES_DATA['puppy-growth-2-12-mo']).toBeDefined();
    expect(ARTICLES_DATA['coat-skin-health']).toBeDefined();

    expect(ARTICLES_DATA['puppy-nutrition-0-2-mo'].title).toContain('Puppy Nutrition Guide');
    expect(ARTICLES_DATA['puppy-growth-2-12-mo'].title).toContain('Puppy Growth Tracker');
    expect(ARTICLES_DATA['coat-skin-health'].title).toContain('Coat & Skin Health Masterclass');
  });

  test('All guide articles are veterinary approved and contain rich sections', () => {
    Object.values(ARTICLES_DATA).forEach((article) => {
      expect(article.veterinaryApproved).toBe(true);
      expect(article.readTimeMins).toBeGreaterThan(0);
      expect(article.keyTakeaways.length).toBeGreaterThan(0);
      expect(article.sections.length).toBeGreaterThan(0);
      article.sections.forEach((sec) => {
        expect(sec.heading.length).toBeGreaterThan(0);
        expect(sec.body.length).toBeGreaterThan(0);
      });
    });
  });

  test('Related guides establish valid non-circular reference links', () => {
    Object.values(ARTICLES_DATA).forEach((article) => {
      article.relatedGuideIds.forEach((relId) => {
        expect(ARTICLES_DATA[relId]).toBeDefined();
        expect(relId).not.toEqual(article.id);
      });
    });
  });
});
